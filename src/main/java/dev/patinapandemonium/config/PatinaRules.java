package dev.patinapandemonium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    public int schemaVersion = 8;
    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public int maximumCreativeTabItems = 0;
    public int maximumCreativePreviewSources = 0;
    public double oxidationAttemptChance = 0.05688889D;
    public int maximumCachedModelParts = 2_048;
    public int maximumCachedItemQuads = 4_096;
    public JsonObject textureOverrides = new JsonObject();
    public JsonObject existingFormOverrides = new JsonObject();

    public boolean namespaceAllowed(String namespace) {
        return !namespace.equals(PatinaPandemonium.MOD_ID)
            && (this.excludedNamespaces == null || !this.excludedNamespaces.contains(namespace));
    }

    private static PatinaRules load() {
        try {
            Files.createDirectories(FILE.getParent());
            PatinaRules rules = Files.exists(FILE)
                ? GSON.fromJson(JsonParser.parseString(Files.readString(FILE)), PatinaRules.class)
                : new PatinaRules();
            if (rules == null) rules = new PatinaRules();
            rules.schemaVersion = 8;
            if (rules.excludedNamespaces == null) rules.excludedNamespaces = new HashSet<>();
            if (rules.excludedBlocks == null) rules.excludedBlocks = new HashSet<>();
            if (rules.textureOverrides == null) rules.textureOverrides = new JsonObject();
            if (rules.existingFormOverrides == null) rules.existingFormOverrides = new JsonObject();
            rules.maximumCreativeTabItems = Math.max(0, rules.maximumCreativeTabItems);
            rules.maximumCreativePreviewSources = Math.max(0, rules.maximumCreativePreviewSources);
            rules.maximumCachedModelParts = Math.max(0, rules.maximumCachedModelParts);
            rules.maximumCachedItemQuads = Math.max(0, rules.maximumCachedItemQuads);
            rules.oxidationAttemptChance = Math.max(0.0D, Math.min(1.0D, rules.oxidationAttemptChance));
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not load {}, using defaults", FILE, error);
            return new PatinaRules();
        }
    }
}
