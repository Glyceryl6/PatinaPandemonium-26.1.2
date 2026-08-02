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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Registers two always-active packs. Minecraft uses independent format versions for client
 * resources and server data, so keeping them separate avoids an invalid mixed pack.mcmeta.
 */
public class RuntimePack {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Minecraft 26.1.2 format declarations.
    private static final List<Integer> CLIENT_FORMAT = List.of(84, 0);
    private static final List<Integer> SERVER_FORMAT = List.of(101, 1);

    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve(PatinaPandemonium.MOD_ID);
    public static final Path CLIENT_FILE = DIRECTORY.resolve("generated-client-resources.zip");
    public static final Path SERVER_FILE = DIRECTORY.resolve("generated-server-data.zip");

    public static void bootstrap() {
        rebuild(List.of());
    }

    public static synchronized void rebuild(List<VariantEntry> entries) {
        Map<String, byte[]> generated = GeneratedPackWriter.build(entries);
        Map<String, byte[]> client = new TreeMap<>();
        Map<String, byte[]> server = new TreeMap<>();
        generated.forEach((path, bytes) -> {
            if (path.startsWith("assets/")) {
                client.put(path, bytes);
            } else if (path.startsWith("data/")) {
                server.put(path, bytes);
            }
        });

        client.put("pack.mcmeta", metadata("Patina Pandemonium generated client resources", CLIENT_FORMAT));
        server.put("pack.mcmeta", metadata("Patina Pandemonium generated server data", SERVER_FORMAT));
        write(CLIENT_FILE, client);
        write(SERVER_FILE, server);
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        Path file;
        String id;
        String displayName;
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            file = CLIENT_FILE;
            id = "patina_pandemonium/generated_client";
            displayName = "Patina Pandemonium Generated Client Resources";
        } else if (event.getPackType() == PackType.SERVER_DATA) {
            file = SERVER_FILE;
            id = "patina_pandemonium/generated_server";
            displayName = "Patina Pandemonium Generated Server Data";
        } else {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                id, Component.literal(displayName),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack pack = Pack.readMetaAndCreate(
                location,
                new FilePackResources.FileResourcesSupplier(file),
                event.getPackType(),
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static byte[] metadata(String description, List<Integer> format) {
        return GSON.toJson(Map.of("pack", Map.of(
                "description", description,
                "min_format", format,
                "max_format", format
        ))).getBytes(StandardCharsets.UTF_8);
    }

    private static void write(Path target, Map<String, byte[]> files) {
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
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            LOGGER.error("Could not write generated pack {}", target, error);
        }
    }

}