package dev.patina_pandemonium.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import dev.patina_pandemonium.registry.VariantProvenance;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Small parameterized advancement tree. Astronomical variant spaces are represented by thresholds, never one advancement per state. */
public class VariantAdvancements {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, MilestoneTrigger> MILESTONE = TRIGGERS.register("milestone", MilestoneTrigger::new);
    private static final int[] GENERATION_THRESHOLDS = {2, 4, 8, 16, 32};
    private static final int[] NAME_COMPLEXITY_THRESHOLDS = {100, 1_000, 10_000, 100_000};
    private static final int[] PROVENANCE_THRESHOLDS = {16, 64, 256, 1_024};
    private static final int[] GENETIC_GENERATION_THRESHOLDS = {2, 4, 8, 16};
    private static final int[] INBREEDING_THRESHOLDS = {125, 250, 500, 750};
    private static final int[] HETEROSIS_THRESHOLDS = {100, 250, 500};
    private static final Map<Identifier, byte[]> RESOURCES = createResources();

    public static Map<Identifier, byte[]> resources() {
        return PatinaRules.INSTANCE.enableAdvancements ? RESOURCES : Map.of();
    }

    public static void evaluateItem(ServerPlayer player, ItemStack stack) {
        if (!PatinaRules.INSTANCE.enableAdvancements || stack.isEmpty()) return;
        ItemVariantData variant = DynamicVariantRegistry.peekItemData(stack);
        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        VariantGenetics.Data genetics = VariantGenetics.get(stack);
        if (variant == null && chemistry == null && provenance == null && genetics == null) return;
        trigger(player, Metric.VARIANT_SEEN, 1);
        if (variant != null && variant.customColor() != null) trigger(player, Metric.CUSTOM_RGB, 1);
        if (chemistry != null) {
            trigger(player, Metric.CHEMISTRY_GENERATION, chemistry.generation());
            trigger(player, Metric.NAME_COMPLEXITY, nameComplexity(chemistry));
            trigger(player, Metric.NESTED_CHEMISTRY, maximumNesting(chemistry));
        }
        if (provenance != null) trigger(player, Metric.PROVENANCE_NODES, provenance.nodes().size());
        if (genetics != null) evaluateGenetics(player, genetics);
    }

    public static void evaluateGenetics(ServerPlayer player, VariantGenetics.Data genetics) {
        if (!PatinaRules.INSTANCE.enableAdvancements || genetics == null) return;
        VariantGenetics.TraitSummary traits = VariantGenetics.traitSummary(genetics);
        trigger(player, Metric.GENETIC_GENERATION, genetics.generation());
        trigger(player, Metric.INBREEDING, genetics.inbreedingPermille());
        trigger(player, Metric.HETEROSIS, traits.heterosisPermille());
        if (traits.recessiveHomozygotes() > 0) trigger(player, Metric.RECESSIVE_LOAD, traits.recessiveHomozygotes());
        if (genetics.mutations() > 0) trigger(player, Metric.MUTATION, genetics.mutations());
        if (genetics.recombinations() > 0) trigger(player, Metric.RECOMBINATION, genetics.recombinations());
    }

    public static void interaction(ServerPlayer player, Metric metric) {
        if (PatinaRules.INSTANCE.enableAdvancements) trigger(player, metric, 1);
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
            if (complexity >= 100_000L) return 100_000;
        }
        return (int) complexity;
    }

    private static Map<Identifier, byte[]> createResources() {
        LinkedHashMap<Identifier, byte[]> resources = new LinkedHashMap<>();
        add(resources, "root", null, Metric.VARIANT_SEEN, 1, "minecraft:copper_block", "task", false);
        add(resources, "custom_rgb", "root", Metric.CUSTOM_RGB, 1, "minecraft:red_dye", "goal", true);
        add(resources, "nested_chemistry", "root", Metric.NESTED_CHEMISTRY, 2, "minecraft:crafting_table", "goal", true);
        addChain(resources, "chemistry_generation", "root", Metric.CHEMISTRY_GENERATION, GENERATION_THRESHOLDS, "minecraft:copper_ingot");
        addChain(resources, "name_complexity", "nested_chemistry", Metric.NAME_COMPLEXITY, NAME_COMPLEXITY_THRESHOLDS, "minecraft:name_tag");
        addChain(resources, "provenance_nodes", "root", Metric.PROVENANCE_NODES, PROVENANCE_THRESHOLDS, "minecraft:book");
        add(resources, "wax_block", "root", Metric.WAX_BLOCK, 1, "minecraft:honeycomb", "task", true);
        add(resources, "scrape_block", "wax_block", Metric.SCRAPE_BLOCK, 1, "minecraft:iron_axe", "task", true);
        add(resources, "wax_entity", "root", Metric.WAX_ENTITY, 1, "minecraft:honeycomb", "task", true);
        add(resources, "scrape_entity", "wax_entity", Metric.SCRAPE_ENTITY, 1, "minecraft:iron_axe", "task", true);
        add(resources, "recombination", "root", Metric.RECOMBINATION, 1, "minecraft:wheat", "goal", true);
        add(resources, "mutation", "recombination", Metric.MUTATION, 1, "minecraft:spider_eye", "challenge", true);
        add(resources, "recessive_load", "recombination", Metric.RECESSIVE_LOAD, 1, "minecraft:poisonous_potato", "challenge", true);
        addChain(resources, "genetic_generation", "recombination", Metric.GENETIC_GENERATION, GENETIC_GENERATION_THRESHOLDS, "minecraft:egg");
        addChain(resources, "inbreeding", "recombination", Metric.INBREEDING, INBREEDING_THRESHOLDS, "minecraft:wither_rose");
        addChain(resources, "heterosis", "recombination", Metric.HETEROSIS, HETEROSIS_THRESHOLDS, "minecraft:golden_carrot");
        return Map.copyOf(resources);
    }

    private static void addChain(Map<Identifier, byte[]> resources, String prefix, String rootParent, Metric metric, int[] thresholds, String icon) {
        String parent = rootParent;
        for (int threshold : thresholds) {
            String path = prefix + "_" + threshold;
            add(resources, path, parent, metric, threshold, icon, threshold == thresholds[thresholds.length - 1] ? "challenge" : "goal", true);
            parent = path;
        }
    }

    private static void add(Map<Identifier, byte[]> resources, String path, String parent, Metric metric, int minimum, String icon, String frame, boolean toast) {
        JsonObject root = new JsonObject();
        if (parent != null) root.addProperty("parent", PatinaPandemonium.MOD_ID + ":" + parent);
        JsonObject display = new JsonObject();
        JsonObject iconObject = new JsonObject();
        iconObject.addProperty("id", icon);
        display.add("icon", iconObject);
        display.add("title", translatable("advancement.patina_pandemonium." + path + ".title"));
        display.add("description", translatable("advancement.patina_pandemonium." + path + ".description"));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", "challenge".equals(frame));
        display.addProperty("hidden", false);
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

    private static JsonObject translatable(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }

    public enum Metric {

        VARIANT_SEEN("variant_seen"),
        CUSTOM_RGB("custom_rgb"),
        CHEMISTRY_GENERATION("chemistry_generation"),
        NAME_COMPLEXITY("name_complexity"),
        NESTED_CHEMISTRY("nested_chemistry"),
        PROVENANCE_NODES("provenance_nodes"),
        WAX_BLOCK("wax_block"),
        SCRAPE_BLOCK("scrape_block"),
        WAX_ENTITY("wax_entity"),
        SCRAPE_ENTITY("scrape_entity"),
        RECOMBINATION("recombination"),
        MUTATION("mutation"),
        RECESSIVE_LOAD("recessive_load"),
        GENETIC_GENERATION("genetic_generation"),
        INBREEDING("inbreeding"),
        HETEROSIS("heterosis");

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