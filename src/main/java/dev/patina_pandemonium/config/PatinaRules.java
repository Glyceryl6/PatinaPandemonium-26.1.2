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

    public int schemaVersion = 18;
    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public Set<String> excludedItems = new HashSet<>();
    public boolean generateExternalVariants = true;
    public int maximumCreativeTabItems = 0;
    public int maximumCreativePreviewSources = 0;
    public double oxidationAttemptChance = 0.05688889D;
    public double naturalVariantSpawnChance = 0.015D;
    public int entityOxidationInterval = 1_365;
    public double entityOxidationAttemptChance = 0.05688889D;
    public double entityOxidationRainMultiplier = 1.0D;
    public double waxTetanusMultiplier = 0.65D;
    public double waxFoodRiskMultiplier = 0.5D;
    public double[] naturalVariantStageWeights = {0.0D, 0.50D, 0.32D, 0.18D};
    public double[] tetanusChances = {0.0D, 0.22D, 0.34D, 0.46D};
    public int[] tetanusDurations = {0, 140, 200, 260};
    public double[] foodPoisonChances = {0.0D, 0.05D, 0.15D, 0.30D};
    public int[] foodPoisonDurations = {0, 110, 160, 210};
    public double[] durabilityMultipliers = {1.0D, 0.80D, 0.62D, 0.45D};
    public double[] waxedDurabilityMultipliers = {1.0D, 0.88D, 0.70D, 0.52D};
    public double[] bonemealSuccessChances = {1.0D, 0.82D, 0.58D, 0.34D};
    public double treeLogVariantCoverage = 0.90D;
    public double treeLeafVariantCoverage = 0.82D;
    public int treeScanHorizontalRadius = 14;
    public int treeScanBelow = 3;
    public int treeScanHeight = 40;
    public double villagerVariantTradeChance = 0.30D;
    public double villagerReputationChanceReduction = 0.008D;
    public double villagerMinimumChanceMultiplier = 0.15D;
    public double villagerVariantDiscount = 0.20D;
    public double villagerWaxChance = 0.30D;
    public double[] villagerVariantStageWeights = {0.10D, 0.45D, 0.30D, 0.15D};
    public double containerLootVariantChance = 0.12D;
    public double containerLootWaxChance = 0.22D;
    public double[] containerLootStageWeights = {0.08D, 0.47D, 0.30D, 0.15D};
    public int inventoryOxidationInterval = 32;
    public double inventoryOxidationAttemptChance = 0.05688889D;
    public boolean inventoryOxidationRequiresSky = true;
    public boolean inventoryOxidationRequiresRain = false;
    public boolean inventoryOxidationAffectsCreative = false;
    public int maximumCachedModelParts = 2_048;
    public int maximumCachedItemQuads = 4_096;
    public int chemistryMaximumNumberBits = 65_536;
    public int maximumChemicalNameGroups = 65_536;
    public int maximumProvenanceNodes = 1_024;
    public int maximumProvenanceDepth = 64;
    public int maximumProvenanceContainerDepth = 4;
    public int maximumProvenanceContainerEntries = 128;
    public int maximumProvenanceTooltipNodes = 12;
    public boolean showChemicalNames = true;
    public boolean showProvenanceTooltip = true;
    public boolean trackContainerProvenance = true;
    public boolean trackToolProvenance = true;
    public boolean automaticPolymerLineage = true;
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

            if (previousSchema < 16) rules.maximumChemicalNameGroups = 4_096;
            if (previousSchema < 17) {
                rules.maximumProvenanceNodes = 1_024;
                rules.maximumProvenanceDepth = 64;
                rules.maximumProvenanceContainerDepth = 4;
                rules.maximumProvenanceContainerEntries = 128;
                rules.maximumProvenanceTooltipNodes = 12;
                rules.showProvenanceTooltip = true;
                rules.trackContainerProvenance = true;
                rules.trackToolProvenance = true;
                rules.automaticPolymerLineage = true;
            }
            if (previousSchema < 18) {
                if (!source.has("entityOxidationInterval") || source.get("entityOxidationInterval").getAsInt() == 64) {
                    rules.entityOxidationInterval = 1_365;
                }
                if (!source.has("entityOxidationRainMultiplier") || source.get("entityOxidationRainMultiplier").getAsDouble() == 4.0D) {
                    rules.entityOxidationRainMultiplier = 1.0D;
                }
            }
            rules.schemaVersion = 18;
            if (rules.excludedNamespaces == null) rules.excludedNamespaces = new HashSet<>();
            if (rules.excludedBlocks == null) rules.excludedBlocks = new HashSet<>();
            if (rules.excludedItems == null) rules.excludedItems = new HashSet<>();
            if (rules.textureOverrides == null) rules.textureOverrides = new JsonObject();
            if (rules.existingFormOverrides == null) rules.existingFormOverrides = new JsonObject();
            rules.maximumCreativeTabItems = Math.max(0, rules.maximumCreativeTabItems);
            rules.maximumCreativePreviewSources = Math.max(0, rules.maximumCreativePreviewSources);
            rules.maximumCachedModelParts = Math.max(0, rules.maximumCachedModelParts);
            rules.maximumCachedItemQuads = Math.max(0, rules.maximumCachedItemQuads);
            rules.chemistryMaximumNumberBits = Math.clamp(rules.chemistryMaximumNumberBits, 128, 65_536);
            rules.maximumChemicalNameGroups = Math.clamp(rules.maximumChemicalNameGroups, 1, 65_536);
            rules.maximumProvenanceNodes = Math.clamp(rules.maximumProvenanceNodes, 32, 65_536);
            rules.maximumProvenanceDepth = Math.clamp(rules.maximumProvenanceDepth, 8, 512);
            rules.maximumProvenanceContainerDepth = Math.clamp(rules.maximumProvenanceContainerDepth, 0, 16);
            rules.maximumProvenanceContainerEntries = Math.clamp(rules.maximumProvenanceContainerEntries, 0, 4_096);
            rules.maximumProvenanceTooltipNodes = Math.clamp(rules.maximumProvenanceTooltipNodes, 0, 128);
            rules.treeScanHorizontalRadius = Math.clamp(rules.treeScanHorizontalRadius, 4, 32);
            rules.treeScanBelow = Math.clamp(rules.treeScanBelow, 0, 8);
            rules.treeScanHeight = Math.clamp(rules.treeScanHeight, 8, 96);
            rules.inventoryOxidationInterval = Math.max(1, rules.inventoryOxidationInterval);
            rules.entityOxidationInterval = Math.max(1, rules.entityOxidationInterval);
            rules.oxidationAttemptChance = chance(rules.oxidationAttemptChance);
            rules.naturalVariantSpawnChance = chance(rules.naturalVariantSpawnChance);
            rules.entityOxidationAttemptChance = chance(rules.entityOxidationAttemptChance);
            rules.entityOxidationRainMultiplier = Math.max(1.0D, rules.entityOxidationRainMultiplier);
            rules.waxTetanusMultiplier = chance(rules.waxTetanusMultiplier);
            rules.waxFoodRiskMultiplier = chance(rules.waxFoodRiskMultiplier);
            rules.treeLogVariantCoverage = chance(rules.treeLogVariantCoverage);
            rules.treeLeafVariantCoverage = chance(rules.treeLeafVariantCoverage);
            rules.villagerVariantTradeChance = chance(rules.villagerVariantTradeChance);
            rules.villagerReputationChanceReduction = Math.max(0.0D, rules.villagerReputationChanceReduction);
            rules.villagerMinimumChanceMultiplier = chance(rules.villagerMinimumChanceMultiplier);
            rules.villagerVariantDiscount = chance(rules.villagerVariantDiscount);
            rules.villagerWaxChance = chance(rules.villagerWaxChance);
            rules.containerLootVariantChance = chance(rules.containerLootVariantChance);
            rules.containerLootWaxChance = chance(rules.containerLootWaxChance);
            rules.inventoryOxidationAttemptChance = chance(rules.inventoryOxidationAttemptChance);
            rules.naturalVariantStageWeights = normalized(rules.naturalVariantStageWeights, new double[]{0.0D, 0.50D, 0.32D, 0.18D});
            rules.tetanusChances = normalized(rules.tetanusChances, new double[]{0.0D, 0.22D, 0.34D, 0.46D});
            rules.tetanusDurations = normalized(rules.tetanusDurations, new int[]{0, 140, 200, 260});
            rules.foodPoisonChances = normalized(rules.foodPoisonChances, new double[]{0.0D, 0.05D, 0.15D, 0.30D});
            rules.foodPoisonDurations = normalized(rules.foodPoisonDurations, new int[]{0, 110, 160, 210});
            rules.durabilityMultipliers = normalized(rules.durabilityMultipliers, new double[]{1.0D, 0.80D, 0.62D, 0.45D});
            rules.waxedDurabilityMultipliers = normalized(rules.waxedDurabilityMultipliers, new double[]{1.0D, 0.88D, 0.70D, 0.52D});
            rules.bonemealSuccessChances = normalized(rules.bonemealSuccessChances, new double[]{1.0D, 0.82D, 0.58D, 0.34D});
            rules.villagerVariantStageWeights = normalized(rules.villagerVariantStageWeights, new double[]{0.10D, 0.45D, 0.30D, 0.15D});
            rules.containerLootStageWeights = normalized(rules.containerLootStageWeights, new double[]{0.08D, 0.47D, 0.30D, 0.15D});
            double naturalStageWeight = 0.0D;
            for (int index = 0; index < OxidationStage.values().length; index++) {
                rules.naturalVariantStageWeights[index] = Math.max(0.0D, rules.naturalVariantStageWeights[index]);
                naturalStageWeight += index == 0 ? 0.0D : rules.naturalVariantStageWeights[index];
                rules.tetanusChances[index] = chance(rules.tetanusChances[index]);
                rules.tetanusDurations[index] = Math.max(0, rules.tetanusDurations[index]);
                rules.foodPoisonChances[index] = chance(rules.foodPoisonChances[index]);
                rules.foodPoisonDurations[index] = Math.max(0, rules.foodPoisonDurations[index]);
                rules.durabilityMultipliers[index] = Math.clamp(rules.durabilityMultipliers[index], 0.01D, 1.0D);
                rules.waxedDurabilityMultipliers[index] = Math.clamp(rules.waxedDurabilityMultipliers[index], 0.01D, 1.0D);
                rules.bonemealSuccessChances[index] = chance(rules.bonemealSuccessChances[index]);
                rules.villagerVariantStageWeights[index] = Math.max(0.0D, rules.villagerVariantStageWeights[index]);
                rules.containerLootStageWeights[index] = Math.max(0.0D, rules.containerLootStageWeights[index]);
            }
            rules.naturalVariantStageWeights[0] = 0.0D;
            if (naturalStageWeight <= 0.0D) rules.naturalVariantStageWeights = new double[]{0.0D, 0.50D, 0.32D, 0.18D};
            if (sum(rules.villagerVariantStageWeights) <= 0.0D) rules.villagerVariantStageWeights = new double[]{0.10D, 0.45D, 0.30D, 0.15D};
            if (sum(rules.containerLootStageWeights) <= 0.0D) rules.containerLootStageWeights = new double[]{0.08D, 0.47D, 0.30D, 0.15D};
            normalizeDurabilityOrder(rules);
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not load {}, using defaults", FILE, error);
            return new PatinaRules();
        }
    }

    private static double chance(double value) {
        return Math.clamp(value, 0.0D, 1.0D);
    }

    private static double sum(double[] values) {
        double result = 0.0D;
        for (double value : values) result += value;
        return result;
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
