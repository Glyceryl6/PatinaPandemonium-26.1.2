package dev.patinapandemonium.registry;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import dev.patinapandemonium.resource.RuntimePack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
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
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ArrayList<VariantEntry> ENTRIES = new ArrayList<>();
    private static final ArrayList<Block> GENERATED = new ArrayList<>();
    private static final List<VariantForm> FORMS = List.of(VariantForm.values());
    private static final List<OxidationStage> STAGES = List.of(OxidationStage.values());
    private static final List<Boolean> WAX_STATES = List.of(false, true);
    private static boolean registrationDone;
    private static long generatedBlockStates;

    public static List<VariantEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<Block> generated() {
        return Collections.unmodifiableList(GENERATED);
    }

    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;
        PatinaRules rules = PatinaRules.INSTANCE;
        if (DatagenModLoader.isRunningDataGen() && !rules.enableOptionalRunDataExport) {
            registrationDone = true;
            LOGGER.info("Patina Pandemonium skipped runtime variant registration during runData because enableOptionalRunDataExport is false");
            return;
        }
        registerAllVariants(event, rules);
    }

    /**
     * NeoForge 26.1 keeps builtin registries writable until every registry event has completed. Waiting
     * for the item event gives every mod a chance to finish registering its blocks and construction forms
     * before this compatibility pass snapshots the block registry.
     */
    private static void registerAllVariants(RegisterEvent event, PatinaRules rules) {
        if (registrationDone) return;
        registrationDone = true;
        ENTRIES.clear();
        GENERATED.clear();
        generatedBlockStates = 0;

        List<SourceRef> sources = BuiltInRegistries.BLOCK.entrySet().stream()
            .filter(entry -> isSource(entry.getKey().identifier(), entry.getValue(), rules))
            .map(entry -> new SourceRef(
                entry.getKey().identifier(),
                entry.getValue(),
                candidateStems(entry.getKey().identifier().getPath()),
                isCompletePaletteSource(entry.getKey().identifier())))
            .toList();
        List<VariantForm> enabledForms = FORMS.stream()
            .filter(form -> form == VariantForm.FULL || rules.registerEverySupportedVariant || form.enabled(rules))
            .toList();
        RegistrationEstimate upperBound = estimateUpperBound(sources, enabledForms, rules);
        checkRegistrationEstimate(upperBound, rules);

        Registry<Block> blockRegistry = BuiltInRegistries.BLOCK;
        for (SourceRef source : sources) {
            registerFamily(blockRegistry, source, selectAllForms(source, enabledForms, rules), rules);
        }

        ENTRIES.trimToSize();
        GENERATED.trimToSize();
        RuntimePack.updateEntries(GENERATED);
        registerItems(event);
        long generatedBytes = estimatedBytes(GENERATED.size(), generatedBlockStates, rules);
        LOGGER.info(
            "Patina Pandemonium processed {} full-block sources and registered every enabled variant: {} generated blocks, {} actual block states and approximately {} MiB of registry/state objects; the conservative preflight upper bound was {} blocks, {} states and {} MiB",
            sources.size(),
            GENERATED.size(),
            generatedBlockStates,
            generatedBytes / 1_048_576L,
            upperBound.blocks(),
            upperBound.states(),
            upperBound.bytes() / 1_048_576L);
    }

    private static BitSet selectAllForms(SourceRef source, List<VariantForm> enabledForms, PatinaRules rules) {
        BitSet selected = new BitSet(FORMS.size() * (DyeColor.VALUES.size() + 1));
        for (VariantForm form : enabledForms) selected.set(laneIndex(null, form));
        if (!dyedVariantsEnabled(rules) || source.completePalette()) return selected;

        for (DyeColor dyeColor : DyeColor.VALUES) {
            for (VariantForm form : enabledForms) selected.set(laneIndex(dyeColor, form));
        }
        return selected;
    }

    private static boolean dyedVariantsEnabled(PatinaRules rules) {
        return rules.registerEverySupportedVariant || rules.dyedVariants;
    }

    private static RegistrationEstimate estimateUpperBound(List<SourceRef> sources, List<VariantForm> forms, PatinaRules rules) {
        long blocks = 0;
        long states = 0;
        for (SourceRef source : sources) {
            int dyedLanes = dyedVariantsEnabled(rules) && !source.completePalette() ? DyeColor.VALUES.size() : 0;
            for (VariantForm form : forms) {
                long uncoloredBlocks = form == VariantForm.FULL ? STAGES.size() * WAX_STATES.size() - 1L : STAGES.size() * WAX_STATES.size();
                long dyedBlocks = (long) dyedLanes * STAGES.size() * WAX_STATES.size();
                long formBlocks = saturatedAdd(uncoloredBlocks, dyedBlocks);
                blocks = saturatedAdd(blocks, formBlocks);
                states = saturatedAdd(states, saturatedMultiply(formBlocks, form.estimatedStateCount()));
            }
        }

        return new RegistrationEstimate(blocks, states, estimatedBytes(blocks, states, rules));
    }

    private static void checkRegistrationEstimate(RegistrationEstimate estimate, PatinaRules rules) {
        long heapWarning = registrationHeapWarning(rules);
        boolean blocksExceeded = rules.warningGeneratedBlocks > 0 && estimate.blocks() > rules.warningGeneratedBlocks;
        boolean statesExceeded = rules.warningGeneratedBlockStates > 0 && estimate.states() > rules.warningGeneratedBlockStates;
        boolean heapExceeded = heapWarning != Long.MAX_VALUE && estimate.bytes() > heapWarning;
        if (!blocksExceeded && !statesExceeded && !heapExceeded) return;
        String message = getString(estimate, rules, heapWarning);
        LOGGER.warn("{} Registration will continue because abortWhenRegistrationEstimateExceedsWarnings is false.", message);
    }

    private static @NonNull String getString(RegistrationEstimate estimate, PatinaRules rules, long heapWarning) {
        String heapLimit = heapWarning == Long.MAX_VALUE ? "disabled" : heapWarning / 1_048_576L + " MiB";
        String message = "Patina Pandemonium full registration preflight estimates " + estimate.blocks() + " generated blocks, "
            + estimate.states() + " block states and " + estimate.bytes() / 1_048_576L + " MiB, above at least one warning threshold ("
            + rules.warningGeneratedBlocks + " blocks, " + rules.warningGeneratedBlockStates + " states, " + heapLimit
            + "). No variants will be silently omitted.";
        if (rules.abortWhenRegistrationEstimateExceedsWarnings) {
            throw new IllegalStateException(message + " Increase the warning thresholds, allocate more heap, disable the abort option or narrow the configured sources/forms.");
        }
        return message;
    }

    private static long registrationHeapWarning(PatinaRules rules) {
        if (!rules.memoryAwareRegistrationWarning) return Long.MAX_VALUE;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long available = Math.max(0L, runtime.maxMemory() - used);
        double fraction = Math.clamp(rules.warningGeneratedHeapFraction, 0.05D, 0.95D);
        return Math.max(0L, (long) (available * fraction));
    }

    private static long estimatedBytes(long blocks, long states, PatinaRules rules) {
        return saturatedAdd(
            saturatedMultiply(blocks, Math.max(1_024, rules.estimatedBytesPerGeneratedBlock)),
            saturatedMultiply(states, Math.max(256, rules.estimatedBytesPerGeneratedBlockState)));
    }

    private static long saturatedMultiply(long value, long multiplier) {
        return value == 0 || multiplier == 0 ? 0 : value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static int laneIndex(@Nullable DyeColor dyeColor, VariantForm form) {
        int colorIndex = dyeColor == null ? 0 : dyeColor.ordinal() + 1;
        return colorIndex * FORMS.size() + form.ordinal();
    }

    private static void registerFamily(Registry<Block> registry, SourceRef source, BitSet selected, PatinaRules rules) {
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
                            Registry.register(registry, blockId, made);
                            block = made;
                            generatedBlockStates += made.getStateDefinition().getPossibleStates().size();
                        } else {
                            blockId = existingId;
                        }

                        if (form == VariantForm.FULL) stageBase = block;
                        family[familyIndex(dyeColor, stage, waxed, form)] = block;
                        if (rules.enableOptionalRunDataExport) {
                            ENTRIES.add(new VariantEntry(data, blockId, block, source.block(), generated));
                        }
                        if (generated) GENERATED.add(block);
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
        for (Block block : GENERATED) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, blockId);
            Item.Properties properties = new Item.Properties().setId(key);
            Item item = new GeneratedBlockItem(block, properties);
            event.register(Registries.ITEM, blockId, () -> item);
        }
    }

    static @Nullable Block resolveBlock(VariantData data) {
        Identifier generatedId = generatedId(data);
        if (BuiltInRegistries.BLOCK.containsKey(generatedId)) return BuiltInRegistries.BLOCK.getValue(generatedId);
        Identifier existingId = findExisting(data, PatinaRules.INSTANCE, candidateStems(data.sourceId().getPath()));
        return existingId == null ? null : BuiltInRegistries.BLOCK.getValue(existingId);
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

        if (isExistingOxidationDerivative(id)) return false;

        BlockState state = block.defaultBlockState();
        try {
            return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                || Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }


    private static boolean isExistingOxidationDerivative(Identifier id) {
        String basePath = id.getPath();
        boolean prefixed = false;
        if (basePath.startsWith("waxed_")) {
            basePath = basePath.substring("waxed_".length());
            prefixed = true;
        }
        for (OxidationStage stage : STAGES) {
            if (stage == OxidationStage.FRESH) continue;
            String prefix = stage.id() + "_";
            if (!basePath.startsWith(prefix)) continue;
            basePath = basePath.substring(prefix.length());
            prefixed = true;
            break;
        }
        if (!prefixed || basePath.isEmpty()) return false;

        Identifier baseId = Identifier.tryBuild(id.getNamespace(), basePath);
        if (baseId == null || !BuiltInRegistries.BLOCK.containsKey(baseId)) return false;
        int existingFamilyMembers = 0;
        for (OxidationStage stage : STAGES) {
            String stagePath = stage == OxidationStage.FRESH ? basePath : stage.id() + "_" + basePath;
            if (existing(id.getNamespace(), stagePath) != null) existingFamilyMembers++;
            if (existing(id.getNamespace(), "waxed_" + stagePath) != null) existingFamilyMembers++;
            if (existingFamilyMembers >= 3) return true;
        }
        return false;
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

    private record RegistrationEstimate(long blocks, long states, long bytes) {}

}