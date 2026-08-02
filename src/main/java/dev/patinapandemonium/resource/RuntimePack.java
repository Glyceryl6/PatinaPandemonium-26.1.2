package dev.patinapandemonium.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.VariantEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Keeps the generated packs valid regardless of whether pack discovery or block registration runs
 * first. Client assets and server data are built independently from the current registry snapshot.
 */
public class RuntimePack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve(PatinaPandemonium.MOD_ID);
    public static final Path CLIENT_FILE = DIRECTORY.resolve("generated-client-resources.zip");
    public static final Path SERVER_FILE = DIRECTORY.resolve("generated-server-data.zip");
    private static final Map<PackType, PackDefinition> PACKS = Map.of(
        PackType.CLIENT_RESOURCES,
        new PackDefinition(
            CLIENT_FILE,
            "generated_client",
            "resourcePack.patina_pandemonium.generated_client",
            "resourcePack.patina_pandemonium.generated_client.description",
            List.of(84, 0)
        ),
        PackType.SERVER_DATA,
        new PackDefinition(
            SERVER_FILE,
            "generated_server",
            "resourcePack.patina_pandemonium.generated_server",
            "resourcePack.patina_pandemonium.generated_server.description",
            List.of(101, 1)
        )
    );
    private static final Set<PackType> REQUESTED = EnumSet.noneOf(PackType.class);
    private static List<VariantEntry> entries = List.of();

    public static synchronized void bootstrap() {
        REQUESTED.clear();
        entries = List.of();
        PACKS.values().forEach(pack -> write(pack.file(), Map.of("pack.mcmeta", metadata(pack))));
    }

    public static synchronized void updateEntries(List<VariantEntry> updatedEntries) {
        entries = List.copyOf(updatedEntries);
        REQUESTED.forEach(RuntimePack::rebuild);
    }

    public static synchronized void onAddPackFinders(AddPackFindersEvent event) {
        PackDefinition definition = PACKS.get(event.getPackType());
        if (definition == null) {
            return;
        }

        REQUESTED.add(event.getPackType());
        rebuild(event.getPackType());

        PackLocationInfo location = new PackLocationInfo(
            PatinaPandemonium.id(definition.idPath()).toString(),
            Component.translatable(definition.titleKey()),
            PackSource.BUILT_IN,
            Optional.empty()
        );
        Pack pack = Pack.readMetaAndCreate(
            location,
            new FilePackResources.FileResourcesSupplier(definition.file()),
            event.getPackType(),
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static void rebuild(PackType packType) {
        PackDefinition definition = PACKS.get(packType);
        Map<String, byte[]> generated = new TreeMap<>(GeneratedPackWriter.build(entries, packType));
        generated.put("pack.mcmeta", metadata(definition));
        if (write(definition.file(), generated)) {
            LOGGER.info("Generated {} runtime pack entries for {}", generated.size() - 1, packType);
        }
    }

    private static byte[] metadata(PackDefinition definition) {
        return GSON.toJson(Map.of("pack", Map.of(
            "description", Map.of("translate", definition.descriptionKey()),
            "min_format", definition.format(),
            "max_format", definition.format()
        ))).getBytes(StandardCharsets.UTF_8);
    }

    private static boolean write(Path target, Map<String, byte[]> files) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            LOGGER.error("Could not write generated pack {}", target, error);
            return false;
        }
    }

    private record PackDefinition(Path file, String idPath, String titleKey, String descriptionKey, List<Integer> format) {}
}
