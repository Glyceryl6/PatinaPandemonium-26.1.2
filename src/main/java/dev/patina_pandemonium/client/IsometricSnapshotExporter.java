package dev.patina_pandemonium.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders the real held ItemStack through Minecraft's GUI/isometric item pipeline into a transparent PNG. */
public class IsometricSnapshotExporter {

    private static final int FULL_BRIGHT_LIGHT = 15_728_880;
    private static final Path EXPORT_DIRECTORY = Path.of("screenshots", "patina_pandemonium", "isometric");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS", Locale.ROOT);
    private static final Renderer RENDERER = new Renderer();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Path exportHeld(ItemStack stack, int size) throws IOException {
        if (stack.isEmpty()) throw new IllegalArgumentException();
        Minecraft minecraft = Minecraft.getInstance();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String safeName = (id.getNamespace() + "__" + id.getPath()).replaceAll("[^a-zA-Z0-9._-]", "_");
        Path path = minecraft.gameDirectory.toPath().resolve(EXPORT_DIRECTORY)
            .resolve(safeName + "_" + FILE_TIME.format(LocalDateTime.now()) + ".png");
        RENDERER.renderAndWrite(minecraft, stack.copyWithCount(1), size, path);
        return path;
    }

    private static class Renderer {

        private final Projection projection = new Projection();
        private final ProjectionMatrixBuffer projectionMatrixBuffer = new ProjectionMatrixBuffer("Patina isometric item export");

        private void renderAndWrite(Minecraft minecraft, ItemStack stack, int size, Path output) throws IOException {
            GpuDevice device = RenderSystem.getDevice();
            RenderTextures textures = new RenderTextures(device, size);
            try {
                Level level = minecraft.level;
                ItemModelResolver resolver = minecraft.getItemModelResolver();
                TrackingItemStackRenderState state = new TrackingItemStackRenderState();
                resolver.updateForTopItem(state, stack, ItemDisplayContext.GUI, level, null, 0);
                FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
                SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
                MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
                Lighting lighting = minecraft.gameRenderer.getLighting();
                CommandEncoder encoder = device.createCommandEncoder();
                encoder.clearColorAndDepthTextures(textures.colorTexture(), 0, textures.depthTexture(), 1.0D);
                RenderSystem.outputColorTextureOverride = textures.colorTextureView();
                RenderSystem.outputDepthTextureOverride = textures.depthTextureView();
                try {
                    this.projection.setupOrtho(-1000.0F, 1000.0F, size, size, true);
                    RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
                    PoseStack poseStack = new PoseStack();
                    poseStack.translate(size / 2.0F, size / 2.0F, 0.0F);
                    poseStack.scale(size, -size, size);
                    lighting.setupFor(state.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
                    state.submit(poseStack, storage, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0);
                    dispatcher.renderAllFeatures();
                    buffers.endBatch();
                } finally {
                    RenderSystem.outputColorTextureOverride = null;
                    RenderSystem.outputDepthTextureOverride = null;
                }

                this.writeTexture(output, textures);
            } catch (IOException | RuntimeException exception) {
                textures.close();
                throw exception;
            }
        }

        private void writeTexture(Path output, RenderTextures textures) throws IOException {
            Files.createDirectories(output.getParent());
            int size = textures.size();
            int pixelSize = textures.colorTexture().getFormat().pixelSize();
            long byteSize = (long) size * size * pixelSize;
            GpuDevice device = RenderSystem.getDevice();
            GpuBuffer buffer = device.createBuffer(() -> "Patina isometric readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, byteSize);
            CommandEncoder encoder = device.createCommandEncoder();
            boolean submitted = false;
            try {
                encoder.copyTextureToBuffer(textures.colorTexture(), buffer, 0L, () -> {
                    try (GpuBuffer.MappedView read = encoder.mapBuffer(buffer, true, false);
                         NativeImage image = new NativeImage(size, size, false)) {
                        for (int y = 0; y < size; y++) {
                            for (int x = 0; x < size; x++) {
                                int pixel = read.data().getInt((x + y * size) * pixelSize);
                                image.setPixelABGR(x, size - y - 1, pixel);
                            }
                        }

                        image.writeToFile(output);
                    } catch (IOException exception) {
                        LOGGER.error(exception.getMessage());
                    } finally {
                        buffer.close();
                        textures.close();
                    }
                }, 0);
                submitted = true;
            } finally {
                if (!submitted) {
                    buffer.close();
                    textures.close();
                }
            }
        }
    }

    private record RenderTextures(GpuTexture colorTexture, GpuTextureView colorTextureView,
                                  GpuTexture depthTexture, GpuTextureView depthTextureView, int size) implements AutoCloseable {

        private RenderTextures(GpuDevice device, int size) {
            this(createColorTexture(device, size), device, size);
        }

        private RenderTextures(GpuTexture colorTexture, GpuDevice device, int size) {
            this(colorTexture, device.createTextureView(colorTexture), createDepthTexture(device, size), device, size);
        }

        private RenderTextures(GpuTexture colorTexture, GpuTextureView colorTextureView, GpuTexture depthTexture, GpuDevice device, int size) {
            this(colorTexture, colorTextureView, depthTexture, device.createTextureView(depthTexture), size);
        }

        private static GpuTexture createColorTexture(GpuDevice device, int size) {
            return device.createTexture(() -> "Patina isometric color",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, size, size, 1, 1);
        }

        private static GpuTexture createDepthTexture(GpuDevice device, int size) {
            return device.createTexture(() -> "Patina isometric depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, size, size, 1, 1);
        }

        @Override
        public void close() {
            this.depthTextureView.close();
            this.colorTextureView.close();
            this.depthTexture.close();
            this.colorTexture.close();
        }
    }
}
