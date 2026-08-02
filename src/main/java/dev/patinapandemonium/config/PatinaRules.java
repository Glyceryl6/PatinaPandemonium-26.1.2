package dev.patinapandemonium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PatinaRules {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve(PatinaPandemonium.MOD_ID + "-rules.json");

    public static final PatinaRules INSTANCE = load();

    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public int maximumGeneratedBlocks = 200_000;
    public boolean slabs = true;
    public boolean stairs = true;
    public boolean walls = true;
    public boolean fences = true;
    public boolean fenceGates = true;
    public boolean buttons = true;
    public boolean pressurePlates = true;
    public boolean signs = true;
    public JsonObject textureOverrides = new JsonObject();
    public JsonObject existingFormOverrides = new JsonObject();

    public boolean namespaceAllowed(String namespace) {
        return !namespace.equals(PatinaPandemonium.MOD_ID) && (excludedNamespaces == null || !excludedNamespaces.contains(namespace));
    }

    private static PatinaRules load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                PatinaRules loaded = GSON.fromJson(Files.readString(FILE), PatinaRules.class);
                return loaded == null ? new PatinaRules() : loaded;
            }
            PatinaRules rules = new PatinaRules();
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Could not load Patina Pandemonium rules; defaults will be used", error);
            return new PatinaRules();
        }
    }

}