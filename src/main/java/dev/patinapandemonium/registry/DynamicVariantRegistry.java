package dev.patinapandemonium.registry;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import dev.patinapandemonium.resource.RuntimePack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<VariantEntry> ENTRIES = new ArrayList<>();
    private static final List<VariantEntry> GENERATED = new ArrayList<>();
    private static final List<List<VariantForm>> FORM_GROUPS = List.of(
        List.of(VariantForm.SLAB),
        List.of(VariantForm.STAIRS),
        List.of(VariantForm.WALL),
        List.of(VariantForm.FENCE),
        List.of(VariantForm.FENCE_GATE),
        List.of(VariantForm.BUTTON),
        List.of(VariantForm.PRESSURE_PLATE));
    private static boolean blocksDone;
    private static long generatedBlockStates;
    private static int skippedSources;
    private static int limitedSources;

    public static List<VariantEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<VariantEntry> generated() {
        return Collections.unmodifiableList(GENERATED);
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            registerBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            registerItems(event);
        }
    }

    private static void registerBlocks(RegisterEvent event) {
        if (blocksDone) {
            return;
        }
        blocksDone = true;

        PatinaRules rules = PatinaRules.INSTANCE;
        List<Map.Entry<ResourceKey<Block>, Block>> sources = BuiltInRegistries.BLOCK.entrySet().stream()
            .filter(entry -> isSource(entry.getKey().identifier(), entry.getValue(), rules))
            .toList();
        long stateBudget = Math.max(0L, Math.min(rules.maximumGeneratedBlockStates, rules.maximumGeneratedBlocks));
        if (rules.memoryAwareBlockStateLimit && stateBudget > 0) {
            long heapBudget = Runtime.getRuntime().maxMemory() / Math.max(1_024, rules.estimatedBytesPerGeneratedBlockState);
            stateBudget = Math.clamp(heapBudget, 8_192L, stateBudget);
        }

        for (int index = 0; index < sources.size(); index++) {
            Map.Entry<ResourceKey<Block>, Block> sourceEntry = sources.get(index);
            long remainingBudget = Math.max(0L, stateBudget - generatedBlockStates);
            long fairBudget = remainingBudget / Math.max(1, sources.size() - index);
            registerFamily(event, sourceEntry.getKey().identifier(), sourceEntry.getValue(), rules, fairBudget, stateBudget);
        }

        RuntimePack.updateEntries(GENERATED);
        LOGGER.info(
            "Patina Pandemonium processed {} full-block sources, limited {} families, skipped {} families, registered {} blocks and {} block states with a {} state budget",
            sources.size(),
            limitedSources,
            skippedSources,
            GENERATED.size(),
            generatedBlockStates,
            stateBudget);
    }

    private static void registerFamily(RegisterEvent event, Identifier sourceId, Block source, PatinaRules rules, long fairBudget, long stateBudget) {
        long fullCost = estimateGeneratedStates(sourceId, rules, List.of(VariantForm.FULL));
        if (generatedBlockStates + fullCost > stateBudget) {
            skippedSources++;
            return;
        }

        Set<VariantForm> selectedForms = selectForms(sourceId, rules, Math.max(fairBudget, fullCost));
        if (selectedForms.size() < enabledFormCount(rules)) {
            limitedSources++;
        }

        Map<String, Block> family = new LinkedHashMap<>();
        for (boolean waxed : new boolean[]{false, true}) {
            for (OxidationStage stage : OxidationStage.values()) {
                Block stageBase = null;
                for (VariantForm form : VariantForm.values()) {
                    if (!selectedForms.contains(form)) {
                        continue;
                    }

                    VariantData data = new VariantData(sourceId, stage, waxed, form);
                    Identifier existingId = findExisting(data, rules);
                    Block block = existingId == null ? null : BuiltInRegistries.BLOCK.getValue(existingId);
                    boolean generated = block == null || block == Blocks.AIR;
                    Identifier blockId;

                    if (form == VariantForm.FULL && stage == OxidationStage.FRESH && !waxed) {
                        block = source;
                        blockId = sourceId;
                        generated = false;
                    } else if (generated) {
                        blockId = generatedId(sourceId, stage, waxed, form);
                        Block base = stageBase == null ? source : stageBase;
                        Block made = GeneratedBlockFactory.create(blockId, source, base, data);
                        event.register(Registries.BLOCK, blockId, () -> made);
                        block = made;
                        generatedBlockStates += made.getStateDefinition().getPossibleStates().size();
                    } else {
                        blockId = existingId;
                    }

                    if (form == VariantForm.FULL) {
                        stageBase = block;
                    }

                    VariantEntry entry = new VariantEntry(data, blockId, block, source, generated);
                    ENTRIES.add(entry);
                    family.put(key(stage, waxed, form), block);
                    if (generated) {
                        GENERATED.add(entry);
                    }
                }
            }
        }

        for (VariantForm form : VariantForm.values()) {
            if (!selectedForms.contains(form)) {
                continue;
            }
            for (OxidationStage stage : OxidationStage.values()) {
                OxidationStage next = stage.next();
                if (next == null) {
                    continue;
                }
                Block from = family.get(key(stage, false, form));
                Block to = family.get(key(next, false, form));
                if (from != null && to != null) {
                    VariantRuntime.linkOxidation(from, to);
                }
            }
        }

        for (VariantForm form : VariantForm.values()) {
            if (!selectedForms.contains(form)) {
                continue;
            }
            for (OxidationStage stage : OxidationStage.values()) {
                Block unwaxed = family.get(key(stage, false, form));
                Block waxed = family.get(key(stage, true, form));
                if (unwaxed != null && waxed != null) {
                    VariantRuntime.linkWaxing(unwaxed, waxed);
                }
            }
        }
    }

    private static Set<VariantForm> selectForms(Identifier sourceId, PatinaRules rules, long budget) {
        Set<VariantForm> selected = EnumSet.of(VariantForm.FULL);
        long used = estimateGeneratedStates(sourceId, rules, selected);
        for (List<VariantForm> group : FORM_GROUPS) {
            long cost = estimateGeneratedStates(sourceId, rules, group);
            if (group.stream().noneMatch(form -> form.enabled(rules))) {
                continue;
            }
            if (cost == 0 || used + cost <= budget) {
                group.stream().filter(form -> form.enabled(rules)).forEach(selected::add);
                used += cost;
            }
        }

        return selected;
    }

    private static int enabledFormCount(PatinaRules rules) {
        return (int) Arrays.stream(VariantForm.values()).filter(form -> form.enabled(rules)).count();
    }

    private static long estimateGeneratedStates(Identifier sourceId, PatinaRules rules, Collection<VariantForm> forms) {
        long states = 0;
        for (boolean waxed : new boolean[]{false, true}) {
            for (OxidationStage stage : OxidationStage.values()) {
                for (VariantForm form : forms) {
                    if (!form.enabled(rules)) continue;
                    VariantData data = new VariantData(sourceId, stage, waxed, form);
                    if (findExisting(data, rules) == null) {
                        states += form.estimatedStateCount();
                    }
                }
            }
        }

        return states;
    }

    private static void registerItems(RegisterEvent event) {
        Map<String, VariantEntry> lookup = new LinkedHashMap<>();
        for (VariantEntry entry : ENTRIES) {
            lookup.put(entryKey(entry.data()), entry);
        }

        for (VariantEntry entry : GENERATED) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, entry.blockId());
            Item.Properties properties = new Item.Properties().setId(key);
            Item item = new GeneratedBlockItem(entry.block(), entry.source(), entry.data(), properties);
            event.register(Registries.ITEM, entry.blockId(), () -> item);
        }
    }

    private static boolean isSource(Identifier id, Block block, PatinaRules rules) {
        if (!rules.namespaceAllowed(id.getNamespace())
            || (rules.excludedBlocks != null && rules.excludedBlocks.contains(id.toString()))
            || block == Blocks.AIR) {
            return false;
        }

        if (block instanceof SlabBlock
            || block instanceof StairBlock
            || block instanceof WallBlock
            || block instanceof FenceBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof PressurePlateBlock
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock) {
            return false;
        }

        String path = id.getPath();
        if (path.startsWith("exposed_")
            || path.startsWith("weathered_")
            || path.startsWith("oxidized_")
            || path.startsWith("waxed_")) {
            return false;
        }

        BlockState state = block.defaultBlockState();
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Identifier generatedId(Identifier source, OxidationStage stage, boolean waxed, VariantForm form) {
        return PatinaPandemonium.id(
            "generated/" + source.getNamespace() + "/" + source.getPath() + "/"
                + (waxed ? "waxed_" : "") + stage.id() + "/" + form.id()
        );
    }

    private static String key(OxidationStage stage, boolean waxed, VariantForm form) {
        return stage.id() + ":" + waxed + ":" + form.id();
    }

    private static String entryKey(VariantData data) {
        return data.sourceId() + "|" + data.stage().id() + "|" + data.waxed() + "|" + data.form().id();
    }

    private static Identifier findExisting(VariantData data, PatinaRules rules) {
        if (data.stage() == OxidationStage.FRESH && !data.waxed() && data.form() == VariantForm.FULL) {
            return data.sourceId();
        }

        JsonElement override = rules.existingFormOverrides == null ? null : rules.existingFormOverrides.get(entryKey(data));
        if (override != null && override.isJsonPrimitive()) {
            Identifier overrideId = Identifier.tryParse(override.getAsString());
            if (overrideId != null && BuiltInRegistries.BLOCK.containsKey(overrideId)) {
                return overrideId;
            }
        }

        String stagePrefix = data.stage() == OxidationStage.FRESH ? "" : data.stage().id() + "_";
        String waxPrefix = data.waxed() ? "waxed_" : "";
        for (String stem : candidateStems(data.sourceId().getPath())) {
            String candidate = waxPrefix + stagePrefix + stem + data.form().suffix();
            Identifier id = Identifier.tryBuild(data.sourceId().getNamespace(), candidate);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return id;
            }
        }

        return null;
    }

    private static List<String> candidateStems(String path) {
        LinkedHashSet<String> stems = new LinkedHashSet<>();
        stems.add(path);
        stripSuffix(stems, path, "_block");
        stripSuffix(stems, path, "_planks");
        stripSuffix(stems, path, "_bricks");
        stripSuffix(stems, path, "_tiles");
        if (path.equals("bricks")) {
            stems.add("brick");
        }
        if (path.endsWith("_bricks")) {
            stems.add(path.substring(0, path.length() - 1));
        }
        if (path.endsWith("_tiles")) {
            stems.add(path.substring(0, path.length() - 1));
        }
        return List.copyOf(stems);
    }

    private static void stripSuffix(Set<String> stems, String path, String suffix) {
        if (path.endsWith(suffix) && path.length() > suffix.length()) {
            stems.add(path.substring(0, path.length() - suffix.length()));
        }
    }
}
