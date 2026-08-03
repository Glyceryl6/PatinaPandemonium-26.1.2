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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<VariantEntry> ENTRIES = new ArrayList<>();
    private static final List<VariantEntry> GENERATED = new ArrayList<>();
    private static final List<VariantForm> CORE_FORM_PRIORITY = List.of(
        VariantForm.STAIRS, VariantForm.SLAB, VariantForm.CARPET);
    private static final List<VariantForm> SECONDARY_FORM_PRIORITY = List.of(
        VariantForm.FENCE, VariantForm.FENCE_GATE, VariantForm.BUTTON,
        VariantForm.PRESSURE_PLATE, VariantForm.WALL);
    private static final List<VariantForm> ALL_FORM_PRIORITY = List.of(
        VariantForm.STAIRS, VariantForm.SLAB, VariantForm.CARPET, VariantForm.FENCE,
        VariantForm.FENCE_GATE, VariantForm.BUTTON, VariantForm.PRESSURE_PLATE, VariantForm.WALL);
    private static final List<VariantForm> FORMS = List.of(VariantForm.values());
    private static final List<OxidationStage> STAGES = List.of(OxidationStage.values());
    private static final List<Boolean> WAX_STATES = List.of(false, true);
    private static boolean blocksDone;
    private static long generatedBlockStates;
    private static long plannedBlocks;
    private static long plannedBlockStates;
    private static long plannedBytes;
    private static int limitedLanes;

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
        if (blocksDone) return;
        blocksDone = true;
        ENTRIES.clear();
        GENERATED.clear();
        generatedBlockStates = 0;
        plannedBlocks = 0;
        plannedBlockStates = 0;
        plannedBytes = 0;
        limitedLanes = 0;
        PatinaRules rules = PatinaRules.INSTANCE;
        List<SourceRef> sources = BuiltInRegistries.BLOCK.entrySet().stream()
            .filter(entry -> isSource(entry.getKey().identifier(), entry.getValue(), rules))
            .map(entry -> new SourceRef(
                    entry.getKey().identifier(), entry.getValue(),
                    candidateStems(entry.getKey().identifier().getPath()),
                    isCompletePaletteSource(entry.getKey().identifier()))).toList();
        Limits limits = new Limits(
            Math.max(0L, rules.maximumGeneratedBlocks),
            Math.max(0L, rules.maximumGeneratedBlockStates),
            memoryBudget(rules));
        Map<SourceRef, BitSet> plans = new LinkedHashMap<>();
        sources.forEach(source -> plans.put(source, new BitSet(FORMS.size() * (DyeColor.VALUES.size() + 1))));
        selectForAll(sources, plans, null, VariantForm.FULL, rules, limits, true);
        for (VariantForm form : CORE_FORM_PRIORITY) {
            if (form.enabled(rules)) selectForAll(sources, plans, null, form, rules, limits, true);
        }

        if (rules.dyedVariants) {
            Limits dyedReserve = phaseLimits(limits, rules.dyedFullBudgetFraction);
            selectDyedFull(sources, plans, rules, dyedReserve, false);
        }

        for (VariantForm form : SECONDARY_FORM_PRIORITY) {
            if (form.enabled(rules)) selectForAll(sources, plans, null, form, rules, limits, true);
        }

        if (rules.dyedVariants) {
            selectDyedFull(sources, plans, rules, limits, true);
            for (VariantForm form : ALL_FORM_PRIORITY) {
                if (!form.enabled(rules)) continue;
                for (DyeColor dyeColor : DyeColor.VALUES) {
                    selectForAll(sources, plans, dyeColor, form, rules, limits, true);
                }
            }
        }

        for (SourceRef source : sources) {
            registerFamily(event, source, plans.get(source), rules);
        }

        RuntimePack.updateEntries(GENERATED);
        String memoryBudget = limits.bytes() == Long.MAX_VALUE ? "unbounded" : limits.bytes() / 1_048_576L + " MiB";
        LOGGER.info(
            "Patina Pandemonium processed {} full-block sources, selected {} generated blocks, {} estimated states and {} MiB, limited {} source/form lanes, registered {} blocks and {} actual block states with budgets of {} blocks, {} states and {}",
            sources.size(), plannedBlocks, plannedBlockStates, plannedBytes / 1_048_576L, limitedLanes, GENERATED.size(), generatedBlockStates, limits.blocks(), limits.states(), memoryBudget);
    }

    private static long memoryBudget(PatinaRules rules) {
        if (!rules.memoryAwareBlockStateLimit) return Long.MAX_VALUE;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long available = Math.max(0L, runtime.maxMemory() - used);
        double fraction = Math.clamp(rules.maximumGeneratedHeapFraction, 0.05D, 0.80D);
        return Math.max(0L, (long) (available * fraction));
    }

    private static Limits phaseLimits(Limits limits, double requestedShare) {
        double share = Math.clamp(requestedShare, 0.0D, 1.0D);
        return new Limits(
            sharedLimit(plannedBlocks, limits.blocks(), share),
            sharedLimit(plannedBlockStates, limits.states(), share),
            sharedLimit(plannedBytes, limits.bytes(), share));
    }

    private static long sharedLimit(long used, long maximum, double share) {
        if (maximum == Long.MAX_VALUE) return maximum;
        return used + (long) ((maximum - used) * share);
    }

    private static void selectDyedFull(List<SourceRef> sources, Map<SourceRef, BitSet> plans, PatinaRules rules, Limits limits, boolean countLimited) {
        for (DyeColor dyeColor : DyeColor.VALUES) {
            selectForAll(sources, plans, dyeColor, VariantForm.FULL, rules, limits, countLimited);
        }
    }

    private static void selectForAll(List<SourceRef> sources, Map<SourceRef, BitSet> plans, @Nullable DyeColor dyeColor,
                                     VariantForm form, PatinaRules rules, Limits limits, boolean countLimited) {
        for (SourceRef source : sources) {
            BitSet selected = plans.get(source);
            if (!canSelect(source, selected, dyeColor, form)) continue;
            Cost cost = estimate(source, dyeColor, form, rules);
            if (fits(plannedBlocks, cost.blocks(), limits.blocks())
                && fits(plannedBlockStates, cost.states(), limits.states())
                && fits(plannedBytes, cost.bytes(), limits.bytes())) {
                selected.set(laneIndex(dyeColor, form));
                plannedBlocks += cost.blocks();
                plannedBlockStates += cost.states();
                plannedBytes += cost.bytes();
            } else if (countLimited && (cost.blocks() > 0 || cost.states() > 0)) {
                limitedLanes++;
            }
        }
    }

    private static boolean fits(long used, long added, long maximum) {
        return used <= maximum && added <= maximum - used;
    }

    private static boolean canSelect(SourceRef source, BitSet selected, @Nullable DyeColor dyeColor, VariantForm form) {
        if (selected.get(laneIndex(dyeColor, form)) || dyeColor != null && source.completePalette()) return false;
        return form == VariantForm.FULL || selected.get(laneIndex(dyeColor, VariantForm.FULL));
    }

    private static int laneIndex(@Nullable DyeColor dyeColor, VariantForm form) {
        int colorIndex = dyeColor == null ? 0 : dyeColor.ordinal() + 1;
        return colorIndex * FORMS.size() + form.ordinal();
    }

    private static Cost estimate(SourceRef source, @Nullable DyeColor dyeColor, VariantForm form, PatinaRules rules) {
        int blocks = 0;
        long states = 0;
        for (boolean waxed : WAX_STATES) {
            for (OxidationStage stage : STAGES) {
                VariantData data = new VariantData(source.id(), stage, waxed, form, dyeColor);
                if (findExisting(data, rules, source.stems()) == null) {
                    blocks++;
                    states += form.estimatedStateCount();
                }
            }
        }

        long bytes = saturatedAdd(
            saturatedMultiply(blocks, Math.max(1_024, rules.estimatedBytesPerGeneratedBlock)),
            saturatedMultiply(states, Math.max(256, rules.estimatedBytesPerGeneratedBlockState)));
        return new Cost(blocks, states, bytes);
    }

    private static long saturatedMultiply(long value, long multiplier) {
        return value == 0 || multiplier == 0 ? 0 : value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static void registerFamily(RegisterEvent event, SourceRef source, BitSet selected, PatinaRules rules) {
        if (selected.isEmpty()) return;
        Block[] family = new Block[(DyeColor.VALUES.size() + 1) * WAX_STATES.size() * STAGES.size() * FORMS.size()];
        for (int colorIndex = -1; colorIndex < DyeColor.VALUES.size(); colorIndex++) {
            DyeColor dyeColor = colorIndex < 0 ? null : DyeColor.VALUES.get(colorIndex);
            if (dyeColor != null && !selected.get(laneIndex(dyeColor, VariantForm.FULL))) continue;
            for (boolean waxed : WAX_STATES) {
                for (OxidationStage stage : STAGES) {
                    Block stageBase = null;
                    for (VariantForm form : FORMS) {
                        if (!selected.get(laneIndex(dyeColor, form))) continue;
                        VariantData data = new VariantData(source.id(), stage, waxed, form, dyeColor);
                        Identifier existingId = findExisting(data, rules, source.stems());
                        Block block = existingId == null ? null : BuiltInRegistries.BLOCK.getValue(existingId);
                        boolean generated = block == null || block == Blocks.AIR;
                        Identifier blockId;
                        if (dyeColor == null && form == VariantForm.FULL && stage == OxidationStage.FRESH && !waxed) {
                            block = source.block();
                            blockId = source.id();
                            generated = false;
                        } else if (generated) {
                            blockId = generatedId(data);
                            Block base = stageBase == null ? source.block() : stageBase;
                            Block made = GeneratedBlockFactory.create(blockId, source.block(), base, data);
                            event.register(Registries.BLOCK, blockId, () -> made);
                            block = made;
                            generatedBlockStates += made.getStateDefinition().getPossibleStates().size();
                        } else {
                            blockId = existingId;
                        }

                        if (form == VariantForm.FULL) stageBase = block;
                        VariantEntry entry = new VariantEntry(data, blockId, block, source.block(), generated);
                        ENTRIES.add(entry);
                        family[familyIndex(dyeColor, stage, waxed, form)] = block;
                        if (generated) GENERATED.add(entry);
                    }
                }
            }
        }

        for (int lane = selected.nextSetBit(0); lane >= 0; lane = selected.nextSetBit(lane + 1)) {
            int colorIndex = lane / FORMS.size() - 1;
            DyeColor dyeColor = colorIndex < 0 ? null : DyeColor.VALUES.get(colorIndex);
            VariantForm form = FORMS.get(lane % FORMS.size());
            for (OxidationStage stage : STAGES) {
                OxidationStage next = stage.next();
                if (next == null) continue;
                Block from = family[familyIndex(dyeColor, stage, false, form)];
                Block to = family[familyIndex(dyeColor, next, false, form)];
                if (from != null && to != null) VariantRuntime.linkOxidation(from, to);
            }

            for (OxidationStage stage : STAGES) {
                Block unwaxed = family[familyIndex(dyeColor, stage, false, form)];
                Block waxed = family[familyIndex(dyeColor, stage, true, form)];
                if (unwaxed != null && waxed != null) VariantRuntime.linkWaxing(unwaxed, waxed);
            }
        }
    }

    private static void registerItems(RegisterEvent event) {
        for (VariantEntry entry : GENERATED) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, entry.blockId());
            Item.Properties properties = new Item.Properties().setId(key);
            Item item = new GeneratedBlockItem(entry.block(), entry.source(), entry.data(), properties);
            event.register(Registries.ITEM, entry.blockId(), () -> item);
        }
    }

    private static boolean isSource(Identifier id, Block block, PatinaRules rules) {
        if (!rules.namespaceAllowed(id.getNamespace())
            || rules.excludedBlocks != null && rules.excludedBlocks.contains(id.toString())
            || block == Blocks.AIR) return false;

        if (block instanceof SlabBlock
            || block instanceof StairBlock
            || block instanceof WallBlock
            || block instanceof FenceBlock
            || block instanceof FenceGateBlock
            || block instanceof CarpetBlock
            || block instanceof ButtonBlock
            || block instanceof PressurePlateBlock
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock) return false;

        String path = id.getPath();
        if (path.startsWith("exposed_") || path.startsWith("weathered_") || path.startsWith("oxidized_") || path.startsWith("waxed_")) return false;

        BlockState state = block.defaultBlockState();
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isCompletePaletteSource(Identifier sourceId) {
        String path = sourceId.getPath();
        for (DyeColor dyeColor : DyeColor.VALUES) {
            String prefix = dyeColor.getSerializedName() + "_";
            if (!path.startsWith(prefix)) continue;
            String stem = path.substring(prefix.length());
            for (DyeColor candidate : DyeColor.VALUES) {
                Identifier sibling = Identifier.tryBuild(sourceId.getNamespace(), candidate.getSerializedName() + "_" + stem);
                if (sibling == null || !BuiltInRegistries.BLOCK.containsKey(sibling)) return false;
            }
            return true;
        }
        return false;
    }

    private static Identifier generatedId(VariantData data) {
        String dyePath = data.dyeColor() == null ? "" : "/dyed/" + data.dyePath();
        return PatinaPandemonium.id(
            "generated/" + data.sourceId().getNamespace() + "/" + data.sourceId().getPath() + "/"
                + (data.waxed() ? "waxed_" : "") + data.stage().id() + dyePath + "/" + data.form().id());
    }

    private static int familyIndex(@Nullable DyeColor dyeColor, OxidationStage stage, boolean waxed, VariantForm form) {
        int colorIndex = dyeColor == null ? 0 : dyeColor.ordinal() + 1;
        return ((colorIndex * WAX_STATES.size() + (waxed ? 1 : 0)) * STAGES.size() + stage.ordinal()) * FORMS.size() + form.ordinal();
    }

    private static String entryKey(VariantData data) {
        String base = data.sourceId() + "|" + data.stage().id() + "|" + data.waxed() + "|" + data.form().id();
        return data.dyeColor() == null ? base : base + "|" + data.dyePath();
    }

    private static @Nullable Identifier findExisting(VariantData data, PatinaRules rules, List<String> stems) {
        if (data.dyeColor() == null && data.stage() == OxidationStage.FRESH && !data.waxed() && data.form() == VariantForm.FULL) return data.sourceId();
        JsonElement override = rules.existingFormOverrides == null ? null : rules.existingFormOverrides.get(entryKey(data));
        if (override != null && override.isJsonPrimitive()) {
            Identifier overrideId = Identifier.tryParse(override.getAsString());
            if (overrideId != null && BuiltInRegistries.BLOCK.containsKey(overrideId)) return overrideId;
        }

        String stagePrefix = data.stage() == OxidationStage.FRESH ? "" : data.stage().id() + "_";
        String waxPrefix = data.waxed() ? "waxed_" : "";
        String dyePrefix = data.dyeColor() == null ? "" : data.dyePath() + "_";
        for (String stem : stems) {
            String suffix = stem + data.form().suffix();
            String first = waxPrefix + stagePrefix + dyePrefix + suffix;
            Identifier found = existing(data.sourceId().getNamespace(), first);
            if (found != null) return found;
            if (data.dyeColor() == null) continue;
            String second = waxPrefix + dyePrefix + stagePrefix + suffix;
            if (!second.equals(first)) {
                found = existing(data.sourceId().getNamespace(), second);
                if (found != null) return found;
            }

            String third = dyePrefix + waxPrefix + stagePrefix + suffix;
            if (!third.equals(first) && !third.equals(second)) {
                found = existing(data.sourceId().getNamespace(), third);
                if (found != null) return found;
            }
        }

        return null;
    }

    private static @Nullable Identifier existing(String namespace, String path) {
        Identifier id = Identifier.tryBuild(namespace, path);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? id : null;
    }

    private static List<String> candidateStems(String path) {
        LinkedHashSet<String> stems = new LinkedHashSet<>();
        stems.add(path);
        if (path.equals("bricks")) stems.add("brick");
        if (path.endsWith("_bricks")) stems.add(path.substring(0, path.length() - 1));
        if (path.endsWith("_tiles")) stems.add(path.substring(0, path.length() - 1));
        stripSuffix(stems, path, "_block");
        stripSuffix(stems, path, "_planks");
        stripSuffix(stems, path, "_bricks");
        stripSuffix(stems, path, "_tiles");
        return List.copyOf(stems);
    }

    private static void stripSuffix(Set<String> stems, String path, String suffix) {
        if (path.endsWith(suffix) && path.length() > suffix.length()) stems.add(path.substring(0, path.length() - suffix.length()));
    }

    private record SourceRef(Identifier id, Block block, List<String> stems, boolean completePalette) {}

    private record Cost(int blocks, long states, long bytes) {}

    private record Limits(long blocks, long states, long bytes) {}

}