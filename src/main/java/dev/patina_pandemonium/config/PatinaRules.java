package dev.patina_pandemonium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.patina_pandemonium.PatinaPandemonium;
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

    public int schemaVersion = 10;
    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public Set<String> excludedItems = new HashSet<>();
    public boolean generateExternalVariants = true;
    public int maximumDelegatedBlockStates = 256;
    public int maximumCreativeTabItems = 0;
    public int maximumCreativePreviewSources = 0;
    public double oxidationAttemptChance = 0.05688889D;
    public int maximumCachedModelParts = 2_048;
    public int maximumCachedItemQuads = 4_096;
    public JsonObject textureOverrides = new JsonObject();
    public JsonObject existingFormOverrides = new JsonObject();

    public boolean namespaceAllowed(String namespace) {
        return !namespace.equals(PatinaPandemonium.MOD_ID)
            && (namespace.equals("minecraft") || this.generateExternalVariants)
            && (this.excludedNamespaces == null || !this.excludedNamespaces.contains(namespace));
    }

    private static PatinaRules load() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject source = Files.exists(FILE)
                ? JsonParser.parseString(Files.readString(FILE)).getAsJsonObject()
                : new JsonObject();
            PatinaRules rules = GSON.fromJson(source, PatinaRules.class);
            if (rules == null) rules = new PatinaRules();
            int previousSchema = source.has("schemaVersion") ? source.get("schemaVersion").getAsInt() : 0;
            if (previousSchema < 9) {
                int legacyMaximumCreativeTabItems = 4_096;
                int legacyMaximumCreativePreviewSources = 256;
                if (source.has("maximumCreativeTabItems")
                    && source.get("maximumCreativeTabItems").getAsInt() == legacyMaximumCreativeTabItems) {
                    rules.maximumCreativeTabItems = 0;
                }

                if (source.has("maximumCreativePreviewSources")
                    && source.get("maximumCreativePreviewSources").getAsInt() == legacyMaximumCreativePreviewSources) {
                    rules.maximumCreativePreviewSources = 0;
                }
            }

            rules.schemaVersion = 10;
            if (rules.excludedNamespaces == null) rules.excludedNamespaces = new HashSet<>();
            if (rules.excludedBlocks == null) rules.excludedBlocks = new HashSet<>();
            if (rules.excludedItems == null) rules.excludedItems = new HashSet<>();
            if (rules.textureOverrides == null) rules.textureOverrides = new JsonObject();
            if (rules.existingFormOverrides == null) rules.existingFormOverrides = new JsonObject();
            rules.maximumCreativeTabItems = Math.max(0, rules.maximumCreativeTabItems);
            rules.maximumCreativePreviewSources = Math.max(0, rules.maximumCreativePreviewSources);
            rules.maximumDelegatedBlockStates = Math.max(1, rules.maximumDelegatedBlockStates);
            rules.maximumCachedModelParts = Math.max(0, rules.maximumCachedModelParts);
            rules.maximumCachedItemQuads = Math.max(0, rules.maximumCachedItemQuads);
            rules.oxidationAttemptChance = Math.clamp(rules.oxidationAttemptChance, 0.0D, 1.0D);
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not load {}, using defaults", FILE, error);
            return new PatinaRules();
        }
    }

}