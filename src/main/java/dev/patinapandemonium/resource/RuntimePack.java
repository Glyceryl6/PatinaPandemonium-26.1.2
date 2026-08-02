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
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exposes generated client and server resources directly from memory. The resource system still
 * receives the formats it expects, but no generated JSON, PNG or ZIP files are written to disk.
 */
public class RuntimePack {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<PackType, PackDefinition> DEFINITIONS = Map.of(
            PackType.CLIENT_RESOURCES,
            new PackDefinition(
                    "generated_client",
                    "resourcePack.patina_pandemonium.generated_client",
                    "resourcePack.patina_pandemonium.generated_client.description"),
            PackType.SERVER_DATA,
            new PackDefinition(
                    "generated_server",
                    "resourcePack.patina_pandemonium.generated_server",
                    "resourcePack.patina_pandemonium.generated_server.description"));
    private static final Map<PackType, ResourceSnapshot> SNAPSHOTS = new EnumMap<>(PackType.class);
    private static List<VariantEntry> entries = List.of();

    public static synchronized void bootstrap() {
        RuntimePack.entries = List.of();
        RuntimePack.SNAPSHOTS.clear();
    }

    public static synchronized void updateEntries(List<VariantEntry> updatedEntries) {
        RuntimePack.entries = List.copyOf(updatedEntries);
        RuntimePack.SNAPSHOTS.clear();
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackDefinition definition = RuntimePack.DEFINITIONS.get(event.getPackType());
        if (definition == null) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                PatinaPandemonium.id(definition.idPath()).toString(),
                Component.translatable(definition.titleKey()),
                PackSource.BUILT_IN, Optional.empty());
        MemoryPackResources resources = new MemoryPackResources(
                location, event.getPackType(),
                definition.descriptionKey());
        Pack pack = Pack.readMetaAndCreate(
                location, BuiltInPackSource.fixedResources(resources), event.getPackType(),
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static synchronized ResourceSnapshot snapshot(PackType packType) {
        return RuntimePack.SNAPSHOTS.computeIfAbsent(packType, RuntimePack::buildSnapshot);
    }

    private static ResourceSnapshot buildSnapshot(PackType packType) {
        long started = System.nanoTime();
        Map<Identifier, byte[]> resources = new HashMap<>();
        Set<String> namespaces = new HashSet<>();
        String directory = packType.getDirectory() + "/";

        GeneratedPackWriter.build(RuntimePack.entries, packType).forEach((path, bytes) -> {
            if (!path.startsWith(directory)) return;
            String relativePath = path.substring(directory.length());
            int separator = relativePath.indexOf('/');
            if (separator <= 0 || separator == relativePath.length() - 1) {
                LOGGER.warn("Ignoring invalid generated resource path {}", path);
                return;
            }

            String namespace = relativePath.substring(0, separator);
            Identifier id = Identifier.tryBuild(namespace, relativePath.substring(separator + 1));
            if (id == null) {
                LOGGER.warn("Ignoring invalid generated resource identifier {}", relativePath);
                return;
            }

            resources.put(id, bytes);
            namespaces.add(namespace);
        });

        ResourceSnapshot snapshot = new ResourceSnapshot(Map.copyOf(resources), Set.copyOf(namespaces));
        LOGGER.info(
                "Prepared {} virtual {} resources in {} ms",
                snapshot.resources().size(), packType,
                (System.nanoTime() - started) / 1_000_000L);
        return snapshot;
    }

    private record PackDefinition(String idPath, String titleKey, String descriptionKey) {
    }

    private record ResourceSnapshot(Map<Identifier, byte[]> resources, Set<String> namespaces) {
    }

    private static class MemoryPackResources implements PackResources {
        private final PackLocationInfo location;
        private final PackType packType;
        private final PackMetadataSection metadata;
        private final byte[] packMetadata;

        private MemoryPackResources(PackLocationInfo location, PackType packType, String descriptionKey) {
            this.location = location;
            this.packType = packType;
            this.metadata = new PackMetadataSection(Component.translatable(descriptionKey),
                    new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(packType)));
            MetadataSectionType<PackMetadataSection> metadataType = packType == PackType.CLIENT_RESOURCES
                    ? PackMetadataSection.CLIENT_TYPE
                    : PackMetadataSection.SERVER_TYPE;
            JsonObject root = new JsonObject();
            root.add("pack", metadataType.codec().encodeStart(JsonOps.INSTANCE, this.metadata).getOrThrow());
            this.packMetadata = GsonHelper.toStableString(root).getBytes(StandardCharsets.UTF_8);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            if (!"pack.mcmeta".equals(String.join("/", path))) return null;
            return () -> new ByteArrayInputStream(this.packMetadata);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType packType, Identifier id) {
            if (packType != this.packType) return null;
            byte[] resource = RuntimePack.snapshot(this.packType).resources().get(id);
            return resource == null ? null : () -> new ByteArrayInputStream(resource);
        }

        @Override
        public void listResources(PackType packType, String namespace, String startingPath, ResourceOutput output) {
            if (packType != this.packType) return;
            String prefix = startingPath.isEmpty() || startingPath.endsWith("/") ? startingPath : startingPath + "/";
            RuntimePack.snapshot(this.packType).resources().forEach((id, bytes) -> {
                if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            return packType == this.packType ? RuntimePack.snapshot(this.packType).namespaces() : Set.of();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            return PackMetadataSection.CLIENT_TYPE.equals(type) || PackMetadataSection.SERVER_TYPE.equals(type) ? (T) this.metadata : null;
        }

        @Override
        public PackLocationInfo location() {
            return this.location;
        }

        @Override
        public void close() {
        }

    }

}