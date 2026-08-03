package dev.patinapandemonium.resource;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.VariantEntry;
import dev.patinapandemonium.registry.VariantForm;
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
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies shared client discovery descriptors and the small set of runtime block tags required by
 * vanilla connection logic. Baked geometry remains flyweight and is installed by the client hook.
 */
public class RuntimePack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> LEGACY_PACKS = List.of("generated-client-resources.zip", "generated-server-data.zip");
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String BLOCKSTATE_DIRECTORY = "blockstates/";
    private static final String ITEM_DIRECTORY = "items/";
    private static final String BLOCK_TAG_DIRECTORY = "tags/block/";
    private static final String JSON_EXTENSION = ".json";
    private static final byte[] BLOCKSTATE_DESCRIPTOR = "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}"
        .getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM_DESCRIPTOR = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/stone\"}}"
        .getBytes(StandardCharsets.UTF_8);
    private static final Map<VariantForm, String> CONNECTION_TAGS = new EnumMap<>(VariantForm.class);
    private static volatile List<VariantEntry> entries = List.of();
    private static volatile Map<Identifier, byte[]> serverResources = Map.of();

    static {
        CONNECTION_TAGS.put(VariantForm.WALL, "walls");
        CONNECTION_TAGS.put(VariantForm.FENCE, "fences");
    }

    public static synchronized void bootstrap() {
        RuntimePack.entries = List.of();
        RuntimePack.serverResources = Map.of();
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
        Map<String, StringBuilder> tags = new LinkedHashMap<>();
        for (VariantEntry entry : updatedEntries) {
            String tag = CONNECTION_TAGS.get(entry.data().form());
            if (tag == null) continue;
            StringBuilder values = tags.computeIfAbsent(tag, ignored -> new StringBuilder("{\"replace\":false,\"values\":["));
            if (values.charAt(values.length() - 1) != '[') values.append(',');
            values.append('\"').append(entry.blockId()).append('\"');
        }

        Map<Identifier, byte[]> resources = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> tag : tags.entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(MINECRAFT_NAMESPACE, BLOCK_TAG_DIRECTORY + tag.getKey() + JSON_EXTENSION);
            resources.put(id, tag.getValue().append("]}").toString().getBytes(StandardCharsets.UTF_8));
        }
        RuntimePack.serverResources = Collections.unmodifiableMap(resources);
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
                byte[] data = RuntimePack.serverResources.get(id);
                return data == null ? null : () -> new ByteArrayInputStream(data);
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
                if (!namespace.equals(MINECRAFT_NAMESPACE)) return;
                for (Map.Entry<Identifier, byte[]> resource : RuntimePack.serverResources.entrySet()) {
                    if (resource.getKey().getPath().startsWith(prefix)) {
                        output.accept(resource.getKey(), () -> new ByteArrayInputStream(resource.getValue()));
                    }
                }
                return;
            }
            if (!namespace.equals(PatinaPandemonium.MOD_ID)) return;
            for (VariantEntry entry : RuntimePack.entries) {
                Identifier blockState = PatinaPandemonium.id(BLOCKSTATE_DIRECTORY + entry.blockId().getPath() + JSON_EXTENSION);
                if (blockState.getPath().startsWith(prefix)) output.accept(blockState, () -> new ByteArrayInputStream(BLOCKSTATE_DESCRIPTOR));
                Identifier item = PatinaPandemonium.id(ITEM_DIRECTORY + entry.blockId().getPath() + JSON_EXTENSION);
                if (item.getPath().startsWith(prefix)) output.accept(item, () -> new ByteArrayInputStream(ITEM_DESCRIPTOR));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            if (packType != this.packType) return Set.of();
            return packType == PackType.CLIENT_RESOURCES ? Set.of(PatinaPandemonium.MOD_ID) : Set.of(MINECRAFT_NAMESPACE);
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
}
