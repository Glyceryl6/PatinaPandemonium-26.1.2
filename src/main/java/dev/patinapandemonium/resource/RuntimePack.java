package dev.patinapandemonium.resource;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies shared client discovery descriptors and streams runtime tags without retaining one large
 * JSON byte array per tag. Baked geometry remains flyweight and is installed by the client hook.
 */
public class RuntimePack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> LEGACY_PACKS = List.of("generated-client-resources.zip", "generated-server-data.zip");
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String BLOCKSTATE_DIRECTORY = "blockstates/";
    private static final String ITEM_DIRECTORY = "items/";
    private static final String BLOCK_TAG_DIRECTORY = "tags/block/";
    private static final String ITEM_TAG_DIRECTORY = "tags/item/";
    private static final String JSON_EXTENSION = ".json";
    private static final byte[] BLOCKSTATE_DESCRIPTOR = "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}"
        .getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM_DESCRIPTOR = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/stone\"}}"
        .getBytes(StandardCharsets.UTF_8);
    private static final Map<Identifier, VariantForm> SERVER_TAGS = new LinkedHashMap<>();
    private static final Set<String> SERVER_NAMESPACES = Set.of(MINECRAFT_NAMESPACE, "c");
    private static volatile List<Block> entries = List.of();

    static {
        addCommonTag(VariantForm.SLAB, "slabs");
        addCommonTag(VariantForm.STAIRS, "stairs");
        addTags(VariantForm.WALL, "walls", "walls");
        addTags(VariantForm.FENCE, "fences", "fences");
        addTags(VariantForm.FENCE_GATE, "fence_gates", "fence_gates");
        addTags(VariantForm.CARPET, "wool_carpets", "carpets");
        addTags(VariantForm.BUTTON, "buttons", "buttons");
        addTags(VariantForm.PRESSURE_PLATE, "pressure_plates", "pressure_plates");
    }

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

    public static synchronized void updateEntries(List<Block> updatedEntries) {
        RuntimePack.entries = Collections.unmodifiableList(updatedEntries);
    }

    private static void addCommonTag(VariantForm form, String path) {
        addTag(form, Identifier.fromNamespaceAndPath("c", path));
    }

    private static void addTags(VariantForm form, String minecraftPath, String commonPath) {
        addTag(form, Identifier.fromNamespaceAndPath(MINECRAFT_NAMESPACE, minecraftPath));
        addTag(form, Identifier.fromNamespaceAndPath("c", commonPath));
    }

    private static void addTag(VariantForm form, Identifier tag) {
        SERVER_TAGS.put(Identifier.fromNamespaceAndPath(tag.getNamespace(), BLOCK_TAG_DIRECTORY + tag.getPath() + JSON_EXTENSION), form);
        SERVER_TAGS.put(Identifier.fromNamespaceAndPath(tag.getNamespace(), ITEM_TAG_DIRECTORY + tag.getPath() + JSON_EXTENSION), form);
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType packType = event.getPackType();
        if (packType != PackType.CLIENT_RESOURCES && packType != PackType.SERVER_DATA) return;
        String kind = packType == PackType.CLIENT_RESOURCES ? "client" : "server";
        PackLocationInfo location = new PackLocationInfo(
            PatinaPandemonium.id("generated_" + kind).toString(),
            Component.translatable("resourcePack.patina_pandemonium.generated_" + kind),
            PackSource.BUILT_IN,
            Optional.empty());
        MemoryPackResources resources = new MemoryPackResources(location, packType, kind);
        Pack pack = Pack.readMetaAndCreate(
            location,
            BuiltInPackSource.fixedResources(resources),
            packType,
            new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static class MemoryPackResources implements PackResources {
        private final PackLocationInfo location;
        private final PackType packType;
        private final MetadataSectionType<PackMetadataSection> metadataType;
        private final PackMetadataSection metadata;
        private final byte[] packMetadata;

        private MemoryPackResources(PackLocationInfo location, PackType packType, String kind) {
            this.location = location;
            this.packType = packType;
            this.metadataType = packType == PackType.CLIENT_RESOURCES
                ? PackMetadataSection.CLIENT_TYPE
                : PackMetadataSection.SERVER_TYPE;
            this.metadata = new PackMetadataSection(
                Component.translatable("resourcePack.patina_pandemonium.generated_" + kind + ".description"),
                new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(packType)));
            JsonObject root = new JsonObject();
            root.add(this.metadataType.name(), this.metadataType.codec().encodeStart(JsonOps.INSTANCE, this.metadata).getOrThrow());
            this.packMetadata = GsonHelper.toStableString(root).getBytes(StandardCharsets.UTF_8);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return "pack.mcmeta".equals(String.join("/", path)) ? () -> new ByteArrayInputStream(this.packMetadata) : null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType packType, Identifier id) {
            if (packType != this.packType) return null;
            if (packType == PackType.SERVER_DATA) {
                VariantForm form = SERVER_TAGS.get(id);
                return form == null ? null : () -> new TagInputStream(RuntimePack.entries.iterator(), form);
            }
            if (!id.getNamespace().equals(PatinaPandemonium.MOD_ID)) return null;
            String path = id.getPath();
            if (path.startsWith(BLOCKSTATE_DIRECTORY) && path.endsWith(JSON_EXTENSION)) return () -> new ByteArrayInputStream(BLOCKSTATE_DESCRIPTOR);
            if (path.startsWith(ITEM_DIRECTORY) && path.endsWith(JSON_EXTENSION)) return () -> new ByteArrayInputStream(ITEM_DESCRIPTOR);
            return null;
        }

        @Override
        public void listResources(PackType packType, String namespace, String startingPath, ResourceOutput output) {
            if (packType != this.packType) return;
            String prefix = startingPath.isEmpty() || startingPath.endsWith("/") ? startingPath : startingPath + "/";
            if (packType == PackType.SERVER_DATA) {
                for (Map.Entry<Identifier, VariantForm> resource : SERVER_TAGS.entrySet()) {
                    if (resource.getKey().getNamespace().equals(namespace) && resource.getKey().getPath().startsWith(prefix)) {
                        output.accept(resource.getKey(), () -> new TagInputStream(RuntimePack.entries.iterator(), resource.getValue()));
                    }
                }
                return;
            }
            if (!namespace.equals(PatinaPandemonium.MOD_ID)) return;
            for (Block block : RuntimePack.entries) {
                Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
                Identifier blockState = PatinaPandemonium.id(BLOCKSTATE_DIRECTORY + blockId.getPath() + JSON_EXTENSION);
                if (blockState.getPath().startsWith(prefix)) output.accept(blockState, () -> new ByteArrayInputStream(BLOCKSTATE_DESCRIPTOR));
                Identifier item = PatinaPandemonium.id(ITEM_DIRECTORY + blockId.getPath() + JSON_EXTENSION);
                if (item.getPath().startsWith(prefix)) output.accept(item, () -> new ByteArrayInputStream(ITEM_DESCRIPTOR));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            if (packType != this.packType) return Set.of();
            return packType == PackType.CLIENT_RESOURCES ? Set.of(PatinaPandemonium.MOD_ID) : SERVER_NAMESPACES;
        }

        @Nullable
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            return this.metadataType.equals(type) ? (T) this.metadata : null;
        }

        @Override
        public PackLocationInfo location() {
            return this.location;
        }

        @Override
        public void close() {}
    }

    private static class TagInputStream extends InputStream {

        private static final byte[] PREFIX = "{\"replace\":false,\"values\":[".getBytes(StandardCharsets.UTF_8);
        private static final byte[] SUFFIX = "]}".getBytes(StandardCharsets.UTF_8);
        private final Iterator<Block> entries;
        private final VariantForm form;
        private byte[] chunk = PREFIX;
        private int offset;
        private boolean first = true;
        private boolean suffixWritten;

        private TagInputStream(Iterator<Block> entries, VariantForm form) {
            this.entries = entries;
            this.form = form;
        }

        @Override
        public int read() {
            while (this.chunk != null && this.offset >= this.chunk.length) this.advance();
            return this.chunk == null ? -1 : this.chunk[this.offset++] & 0xFF;
        }

        @Override
        public int read(byte[] output, int outputOffset, int length) {
            if (length == 0) return 0;
            int written = 0;
            while (written < length) {
                while (this.chunk != null && this.offset >= this.chunk.length) this.advance();
                if (this.chunk == null) break;
                int count = Math.min(length - written, this.chunk.length - this.offset);
                System.arraycopy(this.chunk, this.offset, output, outputOffset + written, count);
                this.offset += count;
                written += count;
            }
            return written == 0 ? -1 : written;
        }

        private void advance() {
            while (this.entries.hasNext()) {
                Block block = this.entries.next();
                VariantData data = ((PatinaOxidizable) block).patinaData();
                if (data.form() != this.form) continue;
                String value = (this.first ? "\"" : ",\"") + BuiltInRegistries.BLOCK.getKey(block) + "\"";
                this.first = false;
                this.chunk = value.getBytes(StandardCharsets.UTF_8);
                this.offset = 0;
                return;
            }
            if (!this.suffixWritten) {
                this.suffixWritten = true;
                this.chunk = SUFFIX;
                this.offset = 0;
                return;
            }
            this.chunk = null;
            this.offset = 0;
        }
    }

}