package dev.patina_pandemonium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.registry.OxidationStage;
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

    public int schemaVersion = 11;
    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public Set<String> excludedItems = new HashSet<>();
    public boolean generateExternalVariants = true;
    public int maximumDelegatedBlockStates = 256;
    public int maximumCreativeTabItems = 0;
    public int maximumCreativePreviewSources = 0;
    public double oxidationAttemptChance = 0.05688889D;
    public double naturalVariantSpawnChance = 0.015D;
    public double waxTetanusMultiplier = 0.65D;
    public double waxFoodRiskMultiplier = 0.5D;
    public double[] naturalVariantStageWeights = {0.0D, 0.50D, 0.32D, 0.18D};
    public double[] tetanusChances = {0.0D, 0.22D, 0.34D, 0.46D};
    public int[] tetanusDurations = {0, 140, 200, 260};
    public double[] foodPoisonChances = {0.0D, 0.05D, 0.15D, 0.30D};
    public int[] foodPoisonDurations = {0, 110, 160, 210};
    public double[] durabilityMultipliers = {1.0D, 0.80D, 0.62D, 0.45D};
    public double[] waxedDurabilityMultipliers = {1.0D, 0.88D, 0.70D, 0.52D};
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

            rules.schemaVersion = 11;
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
            rules.naturalVariantSpawnChance = Math.clamp(rules.naturalVariantSpawnChance, 0.0D, 1.0D);
            rules.waxTetanusMultiplier = Math.clamp(rules.waxTetanusMultiplier, 0.0D, 1.0D);
            rules.waxFoodRiskMultiplier = Math.clamp(rules.waxFoodRiskMultiplier, 0.0D, 1.0D);
            rules.naturalVariantStageWeights = normalized(rules.naturalVariantStageWeights, new double[]{0.0D, 0.50D, 0.32D, 0.18D});
            rules.tetanusChances = normalized(rules.tetanusChances, new double[]{0.0D, 0.22D, 0.34D, 0.46D});
            rules.tetanusDurations = normalized(rules.tetanusDurations, new int[]{0, 140, 200, 260});
            rules.foodPoisonChances = normalized(rules.foodPoisonChances, new double[]{0.0D, 0.05D, 0.15D, 0.30D});
            rules.foodPoisonDurations = normalized(rules.foodPoisonDurations, new int[]{0, 110, 160, 210});
            rules.durabilityMultipliers = normalized(rules.durabilityMultipliers, new double[]{1.0D, 0.80D, 0.62D, 0.45D});
            rules.waxedDurabilityMultipliers = normalized(rules.waxedDurabilityMultipliers, new double[]{1.0D, 0.88D, 0.70D, 0.52D});
            double stageWeight = 0.0D;
            for (int index = 0; index < OxidationStage.values().length; index++) {
                rules.naturalVariantStageWeights[index] = Math.max(0.0D, rules.naturalVariantStageWeights[index]);
                stageWeight += index == 0 ? 0.0D : rules.naturalVariantStageWeights[index];
                rules.tetanusChances[index] = Math.clamp(rules.tetanusChances[index], 0.0D, 1.0D);
                rules.tetanusDurations[index] = Math.max(0, rules.tetanusDurations[index]);
                rules.foodPoisonChances[index] = Math.clamp(rules.foodPoisonChances[index], 0.0D, 1.0D);
                rules.foodPoisonDurations[index] = Math.max(0, rules.foodPoisonDurations[index]);
                rules.durabilityMultipliers[index] = Math.clamp(rules.durabilityMultipliers[index], 0.01D, 1.0D);
                rules.waxedDurabilityMultipliers[index] = Math.clamp(rules.waxedDurabilityMultipliers[index], 0.01D, 1.0D);
            }
            rules.naturalVariantStageWeights[0] = 0.0D;
            if (stageWeight <= 0.0D) rules.naturalVariantStageWeights = new double[]{0.0D, 0.50D, 0.32D, 0.18D};
            normalizeDurabilityOrder(rules);
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not load {}, using defaults", FILE, error);
            return new PatinaRules();
        }
    }

    private static double[] normalized(double[] values, double[] defaults) {
        return values == null || values.length != defaults.length ? defaults.clone() : values;
    }

    private static int[] normalized(int[] values, int[] defaults) {
        return values == null || values.length != defaults.length ? defaults.clone() : values;
    }

    private static void normalizeDurabilityOrder(PatinaRules rules) {
        rules.durabilityMultipliers[0] = 1.0D;
        rules.waxedDurabilityMultipliers[0] = 1.0D;
        for (int index = 1; index < OxidationStage.values().length; index++) {
            double previous = rules.durabilityMultipliers[index - 1];
            double unwaxed = Math.min(rules.durabilityMultipliers[index], Math.nextDown(previous));
            double waxed = Math.max(Math.nextUp(unwaxed), rules.waxedDurabilityMultipliers[index]);
            rules.durabilityMultipliers[index] = unwaxed;
            rules.waxedDurabilityMultipliers[index] = Math.min(waxed, Math.nextDown(previous));
        }
    }

}
