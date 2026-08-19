package dev.patina_pandemonium.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Compact, parameterized advancement system. The advancement count follows gameplay categories and logarithmic
 * milestones rather than the astronomical number of concrete variant states.
 */
public class VariantAdvancements {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, MilestoneTrigger> MILESTONE = TRIGGERS.register("milestone", MilestoneTrigger::new);

    private static final int[] GENERATION_THRESHOLDS = {2, 4, 8, 16, 32};
    private static final int[] NAME_COMPLEXITY_THRESHOLDS = {100, 1_000, 10_000, 100_000};
    private static final int[] PROVENANCE_THRESHOLDS = {16, 64, 256, 1_024};
    private static final int[] PROVENANCE_DEPTH_THRESHOLDS = {8, 16, 32, 64};
    private static final int[] STATE_SPACE_THRESHOLDS = {6, 12, 24, 100, 200, 1_000};
    private static final int[] CONTAINER_DEPTH_THRESHOLDS = {2, 4};
    private static final int[] GENETIC_GENERATION_THRESHOLDS = {2, 4, 8, 16};
    private static final int[] INBREEDING_THRESHOLDS = {125, 250, 500, 750};
    private static final int[] HETEROSIS_THRESHOLDS = {100, 200, 300};
    private static final int[] HETEROZYGOSITY_THRESHOLDS = {500, 750, 900};
    private static final int[] COLOR_DIVERGENCE_THRESHOLDS = {16, 64, 192};
    private static final int NAME_COMPLEXITY_CAP = 1_000_000;
    private static volatile Map<Identifier, byte[]> cachedResources;

    public static Map<Identifier, byte[]> resources() {
        if (!PatinaRules.INSTANCE.enableAdvancements) return Map.of();
        Map<Identifier, byte[]> cached = cachedResources;
        if (cached != null) return cached;
        synchronized (VariantAdvancements.class) {
            if (cachedResources == null) cachedResources = Map.copyOf(createResources());
            return cachedResources;
        }
    }

    public static void evaluateItem(ServerPlayer player, ItemStack stack) {
        if (!PatinaRules.INSTANCE.enableAdvancements || stack.isEmpty()) return;
        ItemVariantData itemVariant = DynamicVariantRegistry.peekItemData(stack);
        VariantData blockVariant = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        VariantGenetics.Data genetics = VariantGenetics.get(stack);
        if (itemVariant == null && blockVariant == null && chemistry == null && provenance == null && genetics == null) return;
        trigger(player, Metric.VARIANT_SEEN, 1);
        OxidationStage stage = itemVariant != null ? itemVariant.stage() : blockVariant == null ? null : blockVariant.stage();
        boolean waxed = itemVariant != null ? itemVariant.waxed() : blockVariant != null && blockVariant.waxed();
        Integer customColor = itemVariant != null ? itemVariant.customColor() : blockVariant == null ? null : blockVariant.customColor();
        if (customColor != null) trigger(player, Metric.CUSTOM_RGB, 1);
        if (stage == OxidationStage.OXIDIZED) trigger(player, Metric.FULL_OXIDATION, 1);
        if (waxed) trigger(player, Metric.WAXED_VARIANT, 1);
        if (chemistry != null) {
            trigger(player, Metric.CHEMISTRY_GENERATION, chemistry.generation());
            trigger(player, Metric.NAME_COMPLEXITY, nameComplexity(chemistry));
            trigger(player, Metric.NESTED_CHEMISTRY, maximumNesting(chemistry));
            trigger(player, Metric.STATE_SPACE_LOG10, stateSpaceMagnitude(chemistry));
        }

        if (provenance != null) evaluateProvenance(player, provenance);
        if (genetics != null) evaluateGenetics(player, genetics);
    }

    public static void evaluateGenetics(ServerPlayer player, VariantGenetics.Data genetics) {
        if (!PatinaRules.INSTANCE.enableAdvancements || genetics == null) return;
        VariantGenetics.TraitSummary traits = VariantGenetics.traitSummary(genetics);
        trigger(player, Metric.GENETIC_GENERATION, genetics.generation());
        trigger(player, Metric.INBREEDING, genetics.inbreedingPermille());
        trigger(player, Metric.HETEROSIS, traits.heterosisPermille());
        trigger(player, Metric.HETEROZYGOSITY, genetics.heterozygosityPermille());
        trigger(player, Metric.GENETIC_COLOR_DIVERGENCE, colorDistance(VariantGenetics.genotypeColor(genetics), genetics.imprintColor()));
        if (traits.recessiveHomozygotes() > 0) trigger(player, Metric.RECESSIVE_LOAD, traits.recessiveHomozygotes());
        if (genetics.mutations() > 0) trigger(player, Metric.MUTATION, genetics.mutations());
        if (genetics.recombinations() > 0) trigger(player, Metric.RECOMBINATION, genetics.recombinations());
    }

    public static void interaction(ServerPlayer player, Metric metric) {
        if (PatinaRules.INSTANCE.enableAdvancements) trigger(player, metric, 1);
    }

    public static void evaluateBrewing(ServerPlayer player, int ingredientCount, List<MobEffectInstance> effects, boolean variantIngredient) {
        if (!PatinaRules.INSTANCE.enableAdvancements) return;
        trigger(player, Metric.BREWING_BOTTLED, 1);
        trigger(player, Metric.BREWING_INGREDIENT_COUNT, ingredientCount);
        trigger(player, Metric.BREWING_EFFECT_COUNT, effects.size());
        int maximumAmplifier = 0;
        int maximumDuration = 0;
        boolean foreign = false;
        for (MobEffectInstance effect : effects) {
            maximumAmplifier = Math.max(maximumAmplifier, effect.getAmplifier());
            maximumDuration = Math.max(maximumDuration, effect.getDuration());
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null && !id.getNamespace().equals("minecraft") && !id.getNamespace().equals(PatinaPandemonium.MOD_ID)) foreign = true;
        }
        trigger(player, Metric.BREWING_MAX_AMPLIFIER, maximumAmplifier);
        trigger(player, Metric.BREWING_MAX_DURATION, maximumDuration);
        if (variantIngredient) trigger(player, Metric.BREWING_VARIANT_INGREDIENT, 1);
        if (foreign) trigger(player, Metric.BREWING_FOREIGN_EFFECT, 1);
    }

    private static void evaluateProvenance(ServerPlayer player, VariantProvenance.Data provenance) {
        trigger(player, Metric.PROVENANCE_NODES, provenance.nodes().size());
        trigger(player, Metric.PROVENANCE_DEPTH, provenance.maximumDepth());
        if (provenance.truncated()) trigger(player, Metric.PROVENANCE_SUMMARY, 1);
        EnumSet<VariantProvenance.NodeType> types = EnumSet.noneOf(VariantProvenance.NodeType.class);
        boolean branched = false;
        boolean cyclic = false;
        boolean crosslinked = false;
        for (VariantProvenance.Node node : provenance.nodes()) {
            types.add(node.type());
            for (String attribute : node.attributes()) {
                switch (attribute) {
                    case "topology=branched" -> branched = true;
                    case "topology=cyclic" -> cyclic = true;
                    case "topology=crosslinked", "topology=network" -> crosslinked = true;
                }
            }
        }

        if (types.contains(VariantProvenance.NodeType.TOOL)) trigger(player, Metric.TOOL_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.EQUIPMENT)) trigger(player, Metric.EQUIPMENT_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.HEAT)) trigger(player, Metric.HEAT_HISTORY, 1);
        if (types.contains(VariantProvenance.NodeType.CONTAINER)) trigger(player, Metric.CONTAINER_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.ENCHANT)) trigger(player, Metric.ENCHANT_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.PIGMENT)) trigger(player, Metric.PIGMENT_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.POLYMER)) trigger(player, Metric.POLYMER_LINEAGE, 1);
        if (types.contains(VariantProvenance.NodeType.LOCAL_STATE_EDIT)) trigger(player, Metric.LOCAL_STATE_HISTORY, 1);
        if (types.contains(VariantProvenance.NodeType.SPLIT_MERGE)) trigger(player, Metric.SPLIT_MERGE_HISTORY, 1);
        if (types.contains(VariantProvenance.NodeType.SUMMARY)) trigger(player, Metric.PROVENANCE_SUMMARY, 1);
        if (branched) trigger(player, Metric.POLYMER_BRANCHED, 1);
        if (cyclic) trigger(player, Metric.POLYMER_CYCLIC, 1);
        if (crosslinked) trigger(player, Metric.POLYMER_CROSSLINKED, 1);
        trigger(player, Metric.CONTAINER_DEPTH, maximumContainerDepth(provenance));
    }

    private static void trigger(ServerPlayer player, Metric metric, int value) {
        MILESTONE.get().trigger(player, metric.id(), value);
    }

    private static int maximumNesting(CraftingChemistry.Data chemistry) {
        int maximum = 0;
        for (int packed : chemistry.groups()) maximum = Math.max(maximum, packed >>> 29 & 0x7);
        return maximum;
    }

    /** Cheap display-size proxy: preserves monotonic growth without materializing a potentially enormous Component every scan tick. */
    private static int nameComplexity(CraftingChemistry.Data chemistry) {
        long complexity = 24L + Integer.toString(chemistry.generation()).length();
        for (int packed : chemistry.groups()) {
            int locant = Math.max(1, packed & 0xFFF);
            int nesting = packed >>> 29 & 0x7;
            complexity += 20L + Integer.toString(locant).length() + nesting * 2L;
            if (complexity >= NAME_COMPLEXITY_CAP) return NAME_COMPLEXITY_CAP;
        }
        return (int) complexity;
    }

    /** Order-of-magnitude estimate only; the actual state space is deliberately never enumerated. */
    private static int stateSpaceMagnitude(CraftingChemistry.Data chemistry) {
        int groups = Math.max(1, chemistry.groups().size());
        double visibleStateLog10 = Math.log10(8.0D * 17.0D);
        long estimate = Math.round(groups * visibleStateLog10 + Math.log10(Math.max(1, chemistry.generation())));
        return (int) Math.clamp(estimate, 0L, Integer.MAX_VALUE);
    }

    private static int maximumContainerDepth(VariantProvenance.Data provenance) {
        int size = provenance.nodes().size();
        int[] memo = new int[size];
        byte[] state = new byte[size];
        int maximum = 0;
        for (int index = 0; index < size; index++) maximum = Math.max(maximum, containerDepth(provenance, index, memo, state));
        return maximum;
    }

    private static int containerDepth(VariantProvenance.Data provenance, int index, int[] memo, byte[] state) {
        if (index < 0 || index >= provenance.nodes().size()) return 0;
        if (state[index] == 2) return memo[index];
        if (state[index] == 1) return 0;
        state[index] = 1;
        VariantProvenance.Node node = provenance.nodes().get(index);
        int childMaximum = 0;
        for (int input : node.inputs()) childMaximum = Math.max(childMaximum, containerDepth(provenance, input, memo, state));
        memo[index] = childMaximum + (node.type() == VariantProvenance.NodeType.CONTAINER ? 1 : 0);
        state[index] = 2;
        return memo[index];
    }

    private static int colorDistance(int first, int second) {
        int red = Math.abs((first >>> 16 & 0xFF) - (second >>> 16 & 0xFF));
        int green = Math.abs((first >>> 8 & 0xFF) - (second >>> 8 & 0xFF));
        int blue = Math.abs((first & 0xFF) - (second & 0xFF));
        return red + green + blue;
    }

    private static Map<Identifier, byte[]> createResources() {
        LinkedHashMap<Identifier, byte[]> resources = new LinkedHashMap<>();
        Random random = new Random(System.nanoTime() ^ (long) BuiltInRegistries.ITEM.size() << 32 ^ BuiltInRegistries.BLOCK.size());
        add(resources, random, "root", null, Metric.VARIANT_SEEN, 1, "minecraft:copper_block", "task", false, false, false);
        add(resources, random, "custom_rgb", "root", Metric.CUSTOM_RGB, 1, "minecraft:red_dye", "goal", true, false, false);
        add(resources, random, "full_oxidation", "custom_rgb", Metric.FULL_OXIDATION, 1, "minecraft:oxidized_copper", "goal", true, false, false);
        add(resources, random, "waxed_variant", "full_oxidation", Metric.WAXED_VARIANT, 1, "minecraft:honeycomb", "goal", true, false, false);
        add(resources, random, "fabricator", "custom_rgb", Metric.FABRICATOR_USE, 1, PatinaPandemonium.MOD_ID + ":variant_fabricator", "goal", true, false, false);
        add(resources, random, "variant_fire", "fabricator", Metric.VARIANT_FIRE_IGNITE, 1, "minecraft:flint_and_steel", "goal", true, false, false);
        add(resources, random, "lightning_clean", "full_oxidation", Metric.LIGHTNING_CLEAN, 1, "minecraft:lightning_rod", "challenge", true, false, false);
        add(resources, random, "variant_trade", "root", Metric.VARIANT_TRADE, 1, "minecraft:emerald", "goal", true, false, false);
        add(resources, random, "oxidized_food", "root", Metric.OXIDIZED_FOOD, 1, "minecraft:poisonous_potato", "goal", true, false, false);
        add(resources, random, "tetanus", "root", Metric.TETANUS, 1, "minecraft:iron_sword", "challenge", true, false, false);
        add(resources, random, "nested_chemistry", "root", Metric.NESTED_CHEMISTRY, 2, "minecraft:crafting_table", "goal", true, false, false);
        addChain(resources, random, "chemistry_generation", "nested_chemistry", Metric.CHEMISTRY_GENERATION, GENERATION_THRESHOLDS, "minecraft:copper_ingot");
        addChain(resources, random, "name_complexity", "nested_chemistry", Metric.NAME_COMPLEXITY, NAME_COMPLEXITY_THRESHOLDS, "minecraft:name_tag");
        addChain(resources, random, "state_space", "nested_chemistry", Metric.STATE_SPACE_LOG10, STATE_SPACE_THRESHOLDS, "minecraft:ender_eye");
        addChain(resources, random, "provenance_nodes", "root", Metric.PROVENANCE_NODES, PROVENANCE_THRESHOLDS, "minecraft:book");
        addChain(resources, random, "provenance_depth", "provenance_nodes_16", Metric.PROVENANCE_DEPTH, PROVENANCE_DEPTH_THRESHOLDS, "minecraft:written_book");
        add(resources, random, "tool_lineage", "provenance_nodes_16", Metric.TOOL_LINEAGE, 1, "minecraft:diamond_pickaxe", "goal", true, false, false);
        add(resources, random, "equipment_lineage", "tool_lineage", Metric.EQUIPMENT_LINEAGE, 1, "minecraft:smithing_table", "goal", true, false, false);
        add(resources, random, "heat_history", "equipment_lineage", Metric.HEAT_HISTORY, 1, "minecraft:blast_furnace", "goal", true, false, false);
        add(resources, random, "container_lineage", "provenance_nodes_16", Metric.CONTAINER_LINEAGE, 1, "minecraft:shulker_box", "goal", true, false, false);
        addChain(resources, random, "container_depth", "container_lineage", Metric.CONTAINER_DEPTH, CONTAINER_DEPTH_THRESHOLDS, "minecraft:bundle");
        add(resources, random, "enchant_lineage", "provenance_nodes_16", Metric.ENCHANT_LINEAGE, 1, "minecraft:enchanted_book", "goal", true, false, false);
        add(resources, random, "pigment_lineage", "provenance_nodes_16", Metric.PIGMENT_LINEAGE, 1, "minecraft:blue_dye", "goal", true, false, false);
        add(resources, random, "local_state_history", "provenance_nodes_16", Metric.LOCAL_STATE_HISTORY, 1, "minecraft:iron_axe", "goal", true, false, false);
        add(resources, random, "split_merge_history", "provenance_nodes_16", Metric.SPLIT_MERGE_HISTORY, 1, "minecraft:stonecutter", "goal", true, false, false);
        add(resources, random, "polymer_lineage", "provenance_nodes_16", Metric.POLYMER_LINEAGE, 1, "minecraft:slime_ball", "goal", true, false, false);
        add(resources, random, "polymer_branched", "polymer_lineage", Metric.POLYMER_BRANCHED, 1, "minecraft:oak_sapling", "goal", true, false, false);
        add(resources, random, "polymer_cyclic", "polymer_branched", Metric.POLYMER_CYCLIC, 1, "minecraft:ender_pearl", "challenge", true, false, false);
        add(resources, random, "polymer_crosslinked", "polymer_cyclic", Metric.POLYMER_CROSSLINKED, 1, "minecraft:chain", "challenge", true, false, false);
        add(resources, random, "provenance_summary", "provenance_nodes_1024", Metric.PROVENANCE_SUMMARY, 1, "minecraft:knowledge_book", "challenge", true, false, false);
        add(resources, random, "wax_block", "root", Metric.WAX_BLOCK, 1, "minecraft:honeycomb", "task", true, false, false);
        add(resources, random, "scrape_block", "wax_block", Metric.SCRAPE_BLOCK, 1, "minecraft:iron_axe", "task", true, false, false);
        add(resources, random, "wax_entity", "root", Metric.WAX_ENTITY, 1, "minecraft:honeycomb", "task", true, false, false);
        add(resources, random, "scrape_entity", "wax_entity", Metric.SCRAPE_ENTITY, 1, "minecraft:iron_axe", "task", true, false, false);
        add(resources, random, "recombination", "root", Metric.RECOMBINATION, 1, "minecraft:wheat", "goal", true, false, false);
        add(resources, random, "mutation", "recombination", Metric.MUTATION, 1, "minecraft:spider_eye", "challenge", true, false, false);
        add(resources, random, "recessive_load", "recombination", Metric.RECESSIVE_LOAD, 1, "minecraft:poisonous_potato", "challenge", true, false, false);
        addChain(resources, random, "genetic_generation", "recombination", Metric.GENETIC_GENERATION, GENETIC_GENERATION_THRESHOLDS, "minecraft:egg");
        addChain(resources, random, "inbreeding", "recombination", Metric.INBREEDING, INBREEDING_THRESHOLDS, "minecraft:wither_rose");
        addChain(resources, random, "heterosis", "recombination", Metric.HETEROSIS, HETEROSIS_THRESHOLDS, "minecraft:golden_carrot");
        addChain(resources, random, "heterozygosity", "recombination", Metric.HETEROZYGOSITY, HETEROZYGOSITY_THRESHOLDS, "minecraft:rabbit_foot");
        addChain(resources, random, "genetic_color_divergence", "recombination", Metric.GENETIC_COLOR_DIVERGENCE, COLOR_DIVERGENCE_THRESHOLDS, "minecraft:magenta_dye");
        add(resources, random, "brewing_cauldron", "root", Metric.BREWING_PRIMED, 1, PatinaPandemonium.MOD_ID + ":seeded_brewing_cauldron", "task", true, false, false);
        add(resources, random, "seeded_brew", "brewing_cauldron", Metric.BREWING_BOTTLED, 1, "minecraft:potion", "goal", true, false, false);
        add(resources, random, "variant_reagent", "seeded_brew", Metric.BREWING_VARIANT_INGREDIENT, 1, "minecraft:oxidized_copper", "goal", true, false, false);
        add(resources, random, "mixed_potion_2", "seeded_brew", Metric.BREWING_EFFECT_COUNT, 2, "minecraft:potion", "goal", true, false, false);
        add(resources, random, "mixed_potion_4", "mixed_potion_2", Metric.BREWING_EFFECT_COUNT, 4, "minecraft:potion", "challenge", true, false, false);
        add(resources, random, "potion_power_4", "mixed_potion_2", Metric.BREWING_MAX_AMPLIFIER, 3, "minecraft:glowstone_dust", "challenge", true, false, false);
        add(resources, random, "long_potion", "seeded_brew", Metric.BREWING_MAX_DURATION, 18_000, "minecraft:redstone", "challenge", true, false, false);
        add(resources, random, "foreign_pharmacology", "mixed_potion_2", Metric.BREWING_FOREIGN_EFFECT, 1, "minecraft:knowledge_book", "challenge", true, false, false);
        add(resources, random, "reagent_chain_16", "seeded_brew", Metric.BREWING_INGREDIENT_COUNT, 16, "minecraft:nether_wart", "challenge", true, false, false);
        addAbsurd(resources, random, "almost_there", Metric.GENETIC_GENERATION, 1_000_000, "minecraft:clock");
        addAbsurd(resources, random, "million_character_name", Metric.NAME_COMPLEXITY, 1_000_000, "minecraft:name_tag");
        addAbsurd(resources, random, "maximal_provenance", Metric.PROVENANCE_NODES, 65_536, "minecraft:enchanted_book");
        addAbsurd(resources, random, "almost_cloned", Metric.INBREEDING, 999, "minecraft:totem_of_undying");
        addAbsurd(resources, random, "impossible_heterosis", Metric.HETEROSIS, 500, "minecraft:nether_star");
        addAbsurd(resources, random, "all_effects_one_bottle", Metric.BREWING_EFFECT_COUNT, 64, "minecraft:dragon_breath");
        addAbsurd(resources, random, "amplifier_255", Metric.BREWING_MAX_AMPLIFIER, 255, "minecraft:glowstone_dust");
        addAbsurd(resources, random, "million_reagents", Metric.BREWING_INGREDIENT_COUNT, 1_000_000, "minecraft:nether_wart");
        addAbsurd(resources, random, "year_long_potion", Metric.BREWING_MAX_DURATION, 630_720_000, "minecraft:clock");
        return resources;
    }

    private static void addChain(Map<Identifier, byte[]> resources, Random random, String prefix, String rootParent, Metric metric, int[] thresholds, String fallbackIcon) {
        String parent = rootParent;
        for (int threshold : thresholds) {
            String path = prefix + "_" + threshold;
            add(resources, random, path, parent, metric, threshold, fallbackIcon,
                threshold == thresholds[thresholds.length - 1] ? "challenge" : "goal", true, false, true);
            parent = path;
        }
    }

    private static void addAbsurd(Map<Identifier, byte[]> resources, Random random, String path, Metric metric, int minimum, String fallbackIcon) {
        add(resources, random, "absurd/" + path, null, metric, minimum, fallbackIcon, "challenge", true, false, true);
    }

    private static void add(Map<Identifier, byte[]> resources, Random random, String path, String parent, Metric metric, int minimum,
                            String fallbackIcon, String frame, boolean toast, boolean hidden, boolean randomIcon) {
        JsonObject root = new JsonObject();
        if (parent != null) root.addProperty("parent", PatinaPandemonium.MOD_ID + ":" + parent);
        JsonObject display = new JsonObject();
        display.add("icon", randomIcon ? randomVariantIcon(random, fallbackIcon) : icon(fallbackIcon));
        String translationPath = path.replace('/', '.');
        display.add("title", translatable("advancement.patina_pandemonium." + translationPath + ".title"));
        display.add("description", translatable("advancement.patina_pandemonium." + translationPath + ".description"));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", "challenge".equals(frame));
        display.addProperty("hidden", hidden);
        if (parent == null) display.addProperty("background", "minecraft:gui/advancements/backgrounds/stone");
        root.add("display", display);
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", PatinaPandemonium.MOD_ID + ":milestone");
        JsonObject conditions = new JsonObject();
        conditions.addProperty("metric", metric.id());
        conditions.addProperty("minimum", minimum);
        criterion.add("conditions", conditions);
        JsonObject criteria = new JsonObject();
        criteria.add("milestone", criterion);
        root.add("criteria", criteria);
        JsonArray requirement = new JsonArray();
        requirement.add("milestone");
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        root.add("requirements", requirements);
        resources.put(PatinaPandemonium.id("advancement/" + path + ".json"), root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject randomVariantIcon(Random random, String fallbackIcon) {
        List<DynamicVariantRegistry.CarrierBinding> blockCandidates = DynamicVariantRegistry.sourceBindings().stream()
            .filter(binding -> binding.item() != null).toList();
        List<Item> itemCandidates = DynamicVariantRegistry.standaloneVariantItems().stream().toList();
        if (blockCandidates.isEmpty() && itemCandidates.isEmpty()) return icon(fallbackIcon);
        if (!itemCandidates.isEmpty() && (blockCandidates.isEmpty() || random.nextBoolean())) {
            Item item = itemCandidates.get(random.nextInt(itemCandidates.size()));
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            JsonObject icon = icon(itemId.toString());
            JsonObject components = new JsonObject();
            JsonObject variant = randomItemVariant(random, itemId);
            components.add(PatinaPandemonium.MOD_ID + ":item_variant_data", variant);
            components.addProperty("minecraft:item_model", DynamicVariantRegistry.VARIANT_ITEM_MODEL.toString());
            icon.add("components", components);
            return icon;
        }

        DynamicVariantRegistry.CarrierBinding binding = blockCandidates.get(random.nextInt(blockCandidates.size()));
        Item item = binding.item();
        if (item == null) return icon(fallbackIcon);
        JsonObject icon = icon(BuiltInRegistries.ITEM.getKey(item).toString());
        JsonObject components = new JsonObject();
        JsonObject variant = randomBlockVariant(random, binding);
        components.add(PatinaPandemonium.MOD_ID + ":variant_data", variant);
        icon.add("components", components);
        return icon;
    }

    private static JsonObject randomBlockVariant(Random random, DynamicVariantRegistry.CarrierBinding binding) {
        JsonObject variant = randomVisualState(random);
        variant.addProperty("source", binding.sourceId().toString());
        variant.addProperty("form", binding.form().ordinal());
        return variant;
    }

    private static JsonObject randomItemVariant(Random random, Identifier itemId) {
        JsonObject variant = randomVisualState(random);
        variant.addProperty("source", itemId.toString());
        variant.addProperty("model", itemId.toString());
        return variant;
    }

    private static JsonObject randomVisualState(Random random) {
        JsonObject variant = new JsonObject();
        variant.addProperty("stage", random.nextInt(OxidationStage.values().length));
        variant.addProperty("waxed", random.nextBoolean());
        boolean customColor = random.nextInt(4) == 0;
        int dye = customColor ? -1 : random.nextInt(DyeColor.VALUES.size() + 1) - 1;
        variant.addProperty("dye", dye);
        if (customColor) variant.addProperty("custom_color", random.nextInt(0x1000000));
        return variant;
    }

    private static JsonObject icon(String id) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", id);
        return icon;
    }

    private static JsonObject translatable(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }

    public enum Metric {

        VARIANT_SEEN("variant_seen"),
        CUSTOM_RGB("custom_rgb"),
        FULL_OXIDATION("full_oxidation"),
        WAXED_VARIANT("waxed_variant"),
        FABRICATOR_USE("fabricator_use"),
        VARIANT_FIRE_IGNITE("variant_fire_ignite"),
        LIGHTNING_CLEAN("lightning_clean"),
        VARIANT_TRADE("variant_trade"),
        OXIDIZED_FOOD("oxidized_food"),
        TETANUS("tetanus"),
        CHEMISTRY_GENERATION("chemistry_generation"),
        NAME_COMPLEXITY("name_complexity"),
        NESTED_CHEMISTRY("nested_chemistry"),
        STATE_SPACE_LOG10("state_space_log10"),
        PROVENANCE_NODES("provenance_nodes"),
        PROVENANCE_DEPTH("provenance_depth"),
        PROVENANCE_SUMMARY("provenance_summary"),
        TOOL_LINEAGE("tool_lineage"),
        EQUIPMENT_LINEAGE("equipment_lineage"),
        HEAT_HISTORY("heat_history"),
        CONTAINER_LINEAGE("container_lineage"),
        CONTAINER_DEPTH("container_depth"),
        ENCHANT_LINEAGE("enchant_lineage"),
        PIGMENT_LINEAGE("pigment_lineage"),
        POLYMER_LINEAGE("polymer_lineage"),
        POLYMER_BRANCHED("polymer_branched"),
        POLYMER_CYCLIC("polymer_cyclic"),
        POLYMER_CROSSLINKED("polymer_crosslinked"),
        LOCAL_STATE_HISTORY("local_state_history"),
        SPLIT_MERGE_HISTORY("split_merge_history"),
        WAX_BLOCK("wax_block"),
        SCRAPE_BLOCK("scrape_block"),
        WAX_ENTITY("wax_entity"),
        SCRAPE_ENTITY("scrape_entity"),
        RECOMBINATION("recombination"),
        MUTATION("mutation"),
        RECESSIVE_LOAD("recessive_load"),
        GENETIC_GENERATION("genetic_generation"),
        INBREEDING("inbreeding"),
        HETEROSIS("heterosis"),
        HETEROZYGOSITY("heterozygosity"),
        GENETIC_COLOR_DIVERGENCE("genetic_color_divergence"),
        BREWING_PRIMED("brewing_primed"),
        BREWING_BOTTLED("brewing_bottled"),
        BREWING_INGREDIENT_COUNT("brewing_ingredient_count"),
        BREWING_EFFECT_COUNT("brewing_effect_count"),
        BREWING_MAX_AMPLIFIER("brewing_max_amplifier"),
        BREWING_MAX_DURATION("brewing_max_duration"),
        BREWING_VARIANT_INGREDIENT("brewing_variant_ingredient"),
        BREWING_FOREIGN_EFFECT("brewing_foreign_effect");

        private final String id;

        Metric(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

    }

    public static class MilestoneTrigger extends SimpleCriterionTrigger<Instance> {

        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String metric, int value) {
            this.trigger(player, instance -> instance.matches(metric, value));
        }

    }

    public record Instance(Optional<ContextAwarePredicate> player, String metric, int minimum) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
            Codec.STRING.fieldOf("metric").forGetter(Instance::metric),
            Codec.INT.optionalFieldOf("minimum", 1).forGetter(Instance::minimum)).apply(instance, Instance::new));

        public boolean matches(String metric, int value) {
            return this.metric.equals(metric) && value >= this.minimum;
        }

    }

}