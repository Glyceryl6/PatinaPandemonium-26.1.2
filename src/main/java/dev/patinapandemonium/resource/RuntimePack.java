package dev.patinapandemonium.resource;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.VariantEntry;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.InclusiveRange;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Supplies only the tiny discovery descriptors required by the vanilla model pipeline. Every
 * generated block shares the same immutable byte arrays; the real baked models are installed by
 * the client model hook, so no per-block model JSON or generated texture is retained in memory.
 */
public class RuntimePack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> LEGACY_PACKS = List.of("generated-client-resources.zip", "generated-server-data.zip");
    private static final String BLOCKSTATE_DIRECTORY = "blockstates/";
    private static final String ITEM_DIRECTORY = "items/";
    private static final String JSON_EXTENSION = ".json";
    private static final byte[] BLOCKSTATE_DESCRIPTOR = "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}"
        .getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM_DESCRIPTOR = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/stone\"}}"
        .getBytes(StandardCharsets.UTF_8);
    private static volatile List<VariantEntry> entries = List.of();

    public static synchronized void bootstrap() {
        RuntimePack.entries = List.of();
        Path legacyDirectory = FMLPaths.CONFIGDIR.get().resolve(PatinaPandemonium.MOD_ID);
        for (String fileName : LEGACY_PACKS) {
            try {
                Files.deleteIfExists(legacyDirectory.resolve(fileName));
            } catch (IOException error) {
                LOGGER.warn("Could not remove legacy generated pack {}", fileName, error);
            }
        }
    }

    public static synchronized void updateEntries(List<VariantEntry> updatedEntries) {
        RuntimePack.entries = Collections.unmodifiableList(updatedEntries);
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
            PatinaPandemonium.id("generated_client").toString(),
            Component.translatable("resourcePack.patina_pandemonium.generated_client"),
            PackSource.BUILT_IN,
            Optional.empty()
        );
        MemoryPackResources resources = new MemoryPackResources(location);
        Pack pack = Pack.readMetaAndCreate(
            location,
            BuiltInPackSource.fixedResources(resources),
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static class MemoryPackResources implements PackResources {
        private final PackLocationInfo location;
        private final PackMetadataSection metadata;
        private final byte[] packMetadata;

        private MemoryPackResources(PackLocationInfo location) {
            this.location = location;
            this.metadata = new PackMetadataSection(
                Component.translatable("resourcePack.patina_pandemonium.generated_client.description"),
                new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES))
            );
            JsonObject root = new JsonObject();
            root.add("pack", PackMetadataSection.CLIENT_TYPE.codec().encodeStart(JsonOps.INSTANCE, this.metadata).getOrThrow());
            this.packMetadata = GsonHelper.toStableString(root).getBytes(StandardCharsets.UTF_8);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return "pack.mcmeta".equals(String.join("/", path))
                ? () -> new ByteArrayInputStream(this.packMetadata)
                : null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType packType, Identifier id) {
            if (packType != PackType.CLIENT_RESOURCES || !id.getNamespace().equals(PatinaPandemonium.MOD_ID)) {
                return null;
            }

            String path = id.getPath();
            if (path.startsWith(BLOCKSTATE_DIRECTORY) && path.endsWith(JSON_EXTENSION)) {
                return () -> new ByteArrayInputStream(BLOCKSTATE_DESCRIPTOR);
            }
            if (path.startsWith(ITEM_DIRECTORY) && path.endsWith(JSON_EXTENSION)) {
                return () -> new ByteArrayInputStream(ITEM_DESCRIPTOR);
            }
            return null;
        }

        @Override
        public void listResources(PackType packType, String namespace, String startingPath, ResourceOutput output) {
            if (packType != PackType.CLIENT_RESOURCES || !namespace.equals(PatinaPandemonium.MOD_ID)) {
                return;
            }

            String prefix = startingPath.isEmpty() || startingPath.endsWith("/") ? startingPath : startingPath + "/";
            List<VariantEntry> snapshot = RuntimePack.entries;
            for (VariantEntry entry : snapshot) {
                Identifier blockState = PatinaPandemonium.id(BLOCKSTATE_DIRECTORY + entry.blockId().getPath() + JSON_EXTENSION);
                if (blockState.getPath().startsWith(prefix)) {
                    output.accept(blockState, () -> new ByteArrayInputStream(BLOCKSTATE_DESCRIPTOR));
                }

                Identifier item = PatinaPandemonium.id(ITEM_DIRECTORY + entry.blockId().getPath() + JSON_EXTENSION);
                if (item.getPath().startsWith(prefix)) {
                    output.accept(item, () -> new ByteArrayInputStream(ITEM_DESCRIPTOR));
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            return packType == PackType.CLIENT_RESOURCES ? Set.of(PatinaPandemonium.MOD_ID) : Set.of();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            return PackMetadataSection.CLIENT_TYPE.equals(type) ? (T) this.metadata : null;
        }

        @Override
        public PackLocationInfo location() {
            return this.location;
        }

        @Override
        public void close() {}
    }
}
