package dev.patina_pandemonium.event;

import dev.patina_pandemonium.block.PatinaBlock;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** Shared runtime context and helpers used by the responsibility-specific gameplay event subscribers. */
public class PatinaGameplayEvents {

    static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
    static final Map<Player, PendingVariantUse> PENDING_USES = new WeakHashMap<>();
    static final Map<ServerLevel, LinkedHashMap<BlockPos, PendingBlockReplacement>> PENDING_REPLACEMENTS = new WeakHashMap<>();
    static final Map<ServerLevel, List<PendingLightningStrike>> PENDING_LIGHTNING_STRIKES = new WeakHashMap<>();
    static final Map<ServerLevel, List<PendingTreeGrowth>> PENDING_TREE_GROWTHS = new WeakHashMap<>();
    static final Map<ServerLevel, LinkedHashMap<BlockPos, PendingToolTransformation>> PENDING_TOOL_TRANSFORMATIONS = new WeakHashMap<>();
    static final Map<Player, Integer> INVENTORY_OXIDATION_CURSORS = new WeakHashMap<>();
    static final Map<LivingEntity, LightningCleanProtection> LIGHTNING_CLEAN_PROTECTION = new WeakHashMap<>();
    static final ThreadLocal<ArrayDeque<VariantUseFrame>> VARIANT_USE_CONTEXT = new ThreadLocal<>();

    public static void beginVariantUse(ItemStack stack) {
        pushVariantUse(DynamicVariantRegistry.variantUseData(stack), stack.get(DynamicVariantRegistry.VARIANT_DATA.get()), VariantProvenance.get(stack),
            stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get()), VariantGenetics.get(stack));
    }

    public static void beginVariantUse(@Nullable VariantData data) {
        pushVariantUse(data == null ? null : itemVariantData(data), data, null, null, null);
    }

    public static void beginDispenserUse(BlockSource source, ItemStack stack) {
        ItemVariantData data = DynamicVariantRegistry.variantUseData(stack);
        VariantData dispenserData = data == null ? DynamicVariantRegistry.blockEntityVariantData(source.blockEntity()) : null;
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        if (provenance == null) provenance = DynamicVariantRegistry.blockEntityProvenance(source.blockEntity());
        pushVariantUse(data != null ? data : dispenserData == null ? null : itemVariantData(dispenserData), stack.get(DynamicVariantRegistry.VARIANT_DATA.get()), provenance,
            stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get()), VariantGenetics.get(stack));
    }

    public static void applyVariantFire(Entity entity, VariantData data) {
        if (entity.level().isClientSide()) return;
        ItemVariantData changed = itemVariantData(data);
        if (changed.equals(entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_FIRE_VARIANT_DATA.get()))) return;
        entity.setData(DynamicVariantRegistry.ENTITY_FIRE_VARIANT_DATA.get(), changed);
    }

    @Nullable
    public static VariantData outputVariant(Block source) {
        ItemVariantData data = currentVariantUse();
        return data == null || DynamicVariantRegistry.fullCarrier(source) == null
            ? null : data.forBlock(BuiltInRegistries.BLOCK.getKey(source));
    }

    static void pushVariantUse(@Nullable ItemVariantData data, @Nullable VariantData blockData, VariantProvenance.@Nullable Data provenance,
                               CraftingChemistry.@Nullable Data chemistry, VariantGenetics.@Nullable Data genetics) {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) {
            contexts = new ArrayDeque<>();
            VARIANT_USE_CONTEXT.set(contexts);
        }

        contexts.push(new VariantUseFrame(data, blockData, provenance, chemistry, genetics));
    }

    public static void endVariantUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return;
        if (!contexts.isEmpty()) contexts.pop();
        if (contexts.isEmpty()) VARIANT_USE_CONTEXT.remove();
    }

    public static void transformGeneratedContainerLoot(Container container, LootParams params, long seed) {
        PatinaRules rules = PatinaRules.INSTANCE;
        RandomSource random = seed == 0L ? params.getLevel().getRandom() : RandomSource.create(seed ^ 0x6A09E667F3BCC909L);
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || random.nextDouble() >= rules.containerLootVariantChance) continue;
            OxidationStage stage = randomStage(random, rules.containerLootStageWeights, false);
            boolean waxed = stage == OxidationStage.FRESH || random.nextDouble() < rules.containerLootWaxChance;
            ItemStack transformed = DynamicVariantRegistry.transform(stack, stage, waxed, null);
            if (transformed.isEmpty()) continue;
            container.setItem(slot, transformed);
            changed = true;
        }

        if (changed) container.setChanged();
    }

    static void processTreeGrowths(ServerLevel level) {
        List<PendingTreeGrowth> pending = PENDING_TREE_GROWTHS.get(level);
        if (pending == null) return;
        Iterator<PendingTreeGrowth> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingTreeGrowth growth = iterator.next();
            if (growth.dueGameTime() > level.getGameTime()) continue;
            List<@Nullable VariantData> variants = new ArrayList<>();
            for (int index = 0; index < growth.saplingPositions().size(); index++) {
                BlockPos saplingPos = growth.saplingPositions().get(index);
                if (sourceBlock(level.getBlockState(saplingPos).getBlock()) != growth.saplingSource()) {
                    variants.add(growth.variants().get(index));
                }
            }

            if (variants.isEmpty()) {
                iterator.remove();
                continue;
            }

            RandomSource random = RandomSource.create(growth.seed());
            for (BlockPos candidate : BlockPos.betweenClosed(growth.min(), growth.max())) {
                if (growth.existingWood().contains(candidate.asLong())) continue;
                BlockState state = level.getBlockState(candidate);
                boolean log = state.is(BlockTags.LOGS);
                if (!log && !state.is(BlockTags.LEAVES)) continue;
                double coverage = log ? PatinaRules.INSTANCE.treeLogVariantCoverage : PatinaRules.INSTANCE.treeLeafVariantCoverage;
                if (random.nextDouble() >= coverage) continue;
                VariantData selected = variants.get(random.nextInt(variants.size()));
                if (selected != null) DynamicVariantRegistry.replaceSourceBlock(level, candidate, state, selected);
            }

            iterator.remove();
        }

        if (pending.isEmpty()) PENDING_TREE_GROWTHS.remove(level);
    }

    @Nullable
    static SaplingGroup saplingGroup(ServerLevel level, BlockPos pos) {
        Block source = sourceBlock(level.getBlockState(pos).getBlock());
        if (!source.defaultBlockState().is(BlockTags.SAPLINGS)) return null;
        for (int xOffset = -1; xOffset <= 0; xOffset++) {
            for (int zOffset = -1; zOffset <= 0; zOffset++) {
                BlockPos origin = pos.offset(xOffset, 0, zOffset);
                List<BlockPos> positions = List.of(origin, origin.east(), origin.south(), origin.east().south());
                if (positions.stream().allMatch(candidate -> sourceBlock(level.getBlockState(candidate).getBlock()) == source)) {
                    return new SaplingGroup(source, positions, positions.stream().map(candidate -> variantData(level, candidate)).toList());
                }
            }
        }

        return new SaplingGroup(source, List.of(pos.immutable()), Collections.singletonList(variantData(level, pos)));
    }

    @Nullable
    static VariantData variantData(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : DynamicVariantRegistry.blockEntityVariantData(blockEntity);
    }

    static void setVariantData(Level level, BlockPos pos, VariantData data) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        VariantData previous = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(blockEntity);
        if (blockEntity instanceof PatinaVariantBlockEntity) PatinaOxidizable.setLinkedData(level, pos, data);
        else DynamicVariantRegistry.setBlockEntityVariantData(blockEntity, data);
        BlockEntity current = level.getBlockEntity(pos);
        if (current != null && chemistry != null && !(current instanceof PatinaVariantBlockEntity)) DynamicVariantRegistry.setBlockEntityChemistry(current,
            CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        if (provenance == null || previous == null || previous.equals(data)) return;
        String operation;
        if (previous.waxed() != data.waxed()) operation = data.waxed() ? "world_wax" : "world_unwax";
        else if (previous.stage() != data.stage()) operation = data.stage().ordinal() > previous.stage().ordinal() ? "world_oxidize" : "world_clean_oxidation";
        else if (!Objects.equals(previous.dyeColor(), data.dyeColor()) || !Objects.equals(previous.customColor(), data.customColor())) operation = "world_recolor";
        else operation = "world_variant_transform";
        if (current != null) DynamicVariantRegistry.setBlockEntityProvenance(current, VariantProvenance.localStateEdit(
            provenance, operation, previous.stage(), data.stage(), data.waxed()));
    }

    static void appendBlockCatalystHistory(ServerLevel level, BlockPos pos, ItemStack catalyst) {
        if (catalyst.isEmpty()) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(blockEntity);
        if (provenance == null) return;
        VariantProvenance.NodeType type = catalyst.getItem() instanceof DyeItem ? VariantProvenance.NodeType.PIGMENT
            : catalyst.canPerformAction(ItemAbilities.AXE_SCRAPE) || catalyst.canPerformAction(ItemAbilities.AXE_WAX_OFF)
            ? VariantProvenance.NodeType.TOOL : VariantProvenance.NodeType.PROCESS;
        if (type == VariantProvenance.NodeType.TOOL && !PatinaRules.INSTANCE.trackToolProvenance) return;
        String operation = type == VariantProvenance.NodeType.PIGMENT ? "world_pigment"
            : type == VariantProvenance.NodeType.TOOL ? "world_tool_edit" : "world_catalyst";
        DynamicVariantRegistry.setBlockEntityProvenance(blockEntity, VariantProvenance.process(provenance, type, operation,
            List.of(catalyst.copy()), VariantProvenance.attributes("target", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))));
    }

    static void appendBlockToolHistory(ServerLevel level, BlockPos pos, ItemStack tool, String operation) {
        if (!PatinaRules.INSTANCE.trackToolProvenance || tool.isEmpty()) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(blockEntity);
        if (provenance == null) return;
        DynamicVariantRegistry.setBlockEntityProvenance(blockEntity, VariantProvenance.process(provenance, VariantProvenance.NodeType.TOOL,
            operation, List.of(tool.copy()), VariantProvenance.attributes("target", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))));
    }

    static void processToolTransformations(ServerLevel level) {
        LinkedHashMap<BlockPos, PendingToolTransformation> pending = PENDING_TOOL_TRANSFORMATIONS.remove(level);
        if (pending == null) return;
        pending.forEach((pos, transformation) -> {
            if (!level.getBlockState(pos).is(transformation.targetBlock())) return;
            setVariantData(level, pos, transformation.data());
            appendBlockToolHistory(level, pos, transformation.tool(), transformation.operation());
        });
    }

    static Block sourceBlock(Block block) {
        if (block instanceof PatinaDelegatingBlock delegated) return delegated.source();
        if (block instanceof PatinaBlock patina) return patina.source();
        return block;
    }

    static OxidationStage randomStage(RandomSource random, double[] weights, boolean excludeFresh) {
        int first = excludeFresh ? 1 : 0;
        double total = 0.0D;
        for (int index = first; index < weights.length; index++) total += weights[index];
        if (total <= 0.0D) return excludeFresh ? OxidationStage.EXPOSED : OxidationStage.FRESH;
        double selected = random.nextDouble() * total;
        for (int index = first; index < weights.length; index++) {
            selected -= weights[index];
            if (selected <= 0.0D) return OxidationStage.byOrdinal(index);
        }

        return OxidationStage.OXIDIZED;
    }

    static void processFireReplacements(ServerLevel level) {
        LinkedHashMap<BlockPos, PendingBlockReplacement> pending = PENDING_REPLACEMENTS.remove(level);
        if (pending == null) return;
        LinkedHashMap<BlockPos, PreparedBlockReplacement> prepared = new LinkedHashMap<>();
        pending.forEach((pos, replacement) -> {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BaseFireBlock) || replacement.previousSource() instanceof BaseFireBlock) return;
            Block source = state.getBlock();
            Block carrier = DynamicVariantRegistry.fullCarrier(source);
            if (carrier == null) return;
            prepared.put(pos, new PreparedBlockReplacement(
                carrier.withPropertiesOf(state),
                replacement.data().forBlock(BuiltInRegistries.BLOCK.getKey(source))));
        });
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        prepared.forEach((pos, replacement) -> {
            level.setBlock(pos, replacement.state(), flags);
            if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(replacement.data());
        });
        prepared.forEach((pos, replacement) -> level.updateNeighborsAt(pos, replacement.state().getBlock()));
    }

    static void processLightningStrikes(ServerLevel level) {
        List<PendingLightningStrike> pending = PENDING_LIGHTNING_STRIKES.get(level);
        if (pending == null) return;
        Iterator<PendingLightningStrike> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingLightningStrike strike = iterator.next();
            if (strike.dueGameTime() > level.getGameTime()) continue;
            cleanPatinaOnLightningStrike(level, strike.pos());
            iterator.remove();
        }

        if (pending.isEmpty()) PENDING_LIGHTNING_STRIKES.remove(level);
    }

    static void cleanPatinaOnLightningStrike(ServerLevel level, BlockPos strikePos) {
        VariantData struckData = variantData(level, strikePos);
        if (struckData != null && !struckData.waxed() && struckData.stage() != OxidationStage.FRESH) {
            setVariantData(level, strikePos, struckData.withStage(OxidationStage.FRESH));
            level.levelEvent(3002, strikePos, -1);
        }

        int walks = level.getRandom().nextInt(3) + 3;
        for (int walk = 0; walk < walks; walk++) {
            BlockPos current = strikePos;
            int steps = level.getRandom().nextInt(8) + 1;
            for (int step = 0; step < steps; step++) {
                BlockPos next = cleanRandomNearbyPatina(level, current);
                if (next == null) break;
                current = next;
            }
        }
    }

    @Nullable
    static BlockPos cleanRandomNearbyPatina(ServerLevel level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.randomInCube(level.getRandom(), 10, pos, 1)) {
            VariantData data = variantData(level, candidate);
            if (data == null) continue;
            if (data.waxed()) continue;
            Optional<VariantData> previous = VariantRuntime.previous(data);
            if (previous.isEmpty()) continue;
            setVariantData(level, candidate, previous.get());
            level.levelEvent(3002, candidate, -1);
            return candidate.immutable();
        }

        return null;
    }

    static void replacePlacedBlock(ServerLevel level, BlockPos pos, BlockState state, ItemVariantData data, VariantProvenance.@Nullable Data provenance, CraftingChemistry.@Nullable Data chemistry) {
        VariantData variant = data.forBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (DynamicVariantRegistry.isNativeBlockEntitySource(state.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                DynamicVariantRegistry.setBlockEntityVariantData(blockEntity, variant);
                if (provenance != null) DynamicVariantRegistry.setBlockEntityProvenance(blockEntity, provenance);
                if (chemistry != null) DynamicVariantRegistry.setBlockEntityChemistry(blockEntity, chemistry);
            }
            return;
        }

        Block carrier = DynamicVariantRegistry.fullCarrier(state.getBlock());
        if (carrier == null) return;
        BlockState target = carrier.withPropertiesOf(state);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        level.setBlock(pos, target, flags);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && provenance != null) DynamicVariantRegistry.setBlockEntityProvenance(blockEntity, provenance);
        if (blockEntity != null && chemistry != null) DynamicVariantRegistry.setBlockEntityChemistry(blockEntity, chemistry);
        if (blockEntity instanceof PatinaVariantBlockEntity patina) patina.setData(variant);
        level.updateNeighborsAt(pos, carrier);
    }

    static void attachPlacedHistory(ServerLevel level, BlockPos pos, VariantProvenance.@Nullable Data provenance, CraftingChemistry.@Nullable Data chemistry) {
        if (provenance == null && chemistry == null) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        if (provenance != null) DynamicVariantRegistry.setBlockEntityProvenance(blockEntity, provenance);
        if (chemistry != null) DynamicVariantRegistry.setBlockEntityChemistry(blockEntity, chemistry);
    }

    static void queueFireReplacement(ServerLevel level, BlockPos pos, ItemVariantData data) {
        Block previousSource = level.getBlockState(pos).getBlock();
        PENDING_REPLACEMENTS.computeIfAbsent(level, _ -> new LinkedHashMap<>())
            .put(pos.immutable(), new PendingBlockReplacement(previousSource, data));
    }

    static boolean matchesPlacementSource(Block source, ItemVariantData data) {
        Item sourceItem = BuiltInRegistries.ITEM.getValue(data.sourceId());
        return Block.byItem(sourceItem) == source || source instanceof BaseFireBlock && IGNITERS.contains(sourceItem)
            || source instanceof RedStoneWireBlock && sourceItem == Items.REDSTONE;
    }

    public static ItemVariantData itemVariantData(VariantData data) {
        Item sourceItem = BuiltInRegistries.BLOCK.getValue(data.sourceId()).asItem();
        Identifier sourceId = sourceItem == Items.AIR ? data.sourceId() : BuiltInRegistries.ITEM.getKey(sourceItem);
        return new ItemVariantData(sourceId, data.stage(), data.waxed(), data.dyeColor(), sourceId, data.customColor());
    }

    @Nullable
    static ItemVariantData currentVariantUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.data() != null) return frame.data();
        }
        return null;
    }

    @Nullable
    static VariantData currentBlockVariantUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.blockData() != null) return frame.blockData();
        }
        return null;
    }

    static VariantProvenance.@Nullable Data currentProvenance() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.provenance() != null) return frame.provenance();
        }
        return null;
    }

    static CraftingChemistry.@Nullable Data currentChemistryUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.chemistry() != null) return frame.chemistry();
        }
        return null;
    }

    static VariantGenetics.@Nullable Data currentGeneticsUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.genetics() != null) return frame.genetics();
        }
        return null;
    }

    @Nullable
    static ItemVariantData placementVariant(@Nullable Player player, Block placedBlock, @Nullable PendingVariantUse pending, @Nullable ItemVariantData context) {
        if (player != null) {
            ItemVariantData mainHand = DynamicVariantRegistry.variantUseData(player.getMainHandItem());
            ItemVariantData offHand = DynamicVariantRegistry.variantUseData(player.getOffhandItem());
            if (mainHand != null && matchesPlacementSource(placedBlock, mainHand)) return mainHand;
            if (offHand != null && matchesPlacementSource(placedBlock, offHand)) return offHand;
        }
        if (context != null && matchesPlacementSource(placedBlock, context)) return context;
        return pending != null && pending.data() != null && matchesPlacementSource(placedBlock, pending.data()) ? pending.data() : null;
    }

    static VariantProvenance.@Nullable Data placementProvenance(@Nullable Player player, Block placedBlock, @Nullable PendingVariantUse pending,
                                                                @Nullable ItemVariantData context, VariantProvenance.@Nullable Data contextProvenance) {
        if (player != null) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            ItemVariantData mainData = DynamicVariantRegistry.variantUseData(mainHand);
            ItemVariantData offData = DynamicVariantRegistry.variantUseData(offHand);
            if (Block.byItem(mainHand.getItem()) == placedBlock || mainData != null && matchesPlacementSource(placedBlock, mainData)) {
                VariantProvenance.Data provenance = VariantProvenance.get(mainHand);
                if (provenance != null) return provenance;
            }
            if (Block.byItem(offHand.getItem()) == placedBlock || offData != null && matchesPlacementSource(placedBlock, offData)) {
                VariantProvenance.Data provenance = VariantProvenance.get(offHand);
                if (provenance != null) return provenance;
            }
        }

        if (context != null && matchesPlacementSource(placedBlock, context)) return contextProvenance;
        if (pending == null) return null;
        boolean matches = Block.byItem(pending.sourceItem()) == placedBlock
                || pending.data() != null && matchesPlacementSource(placedBlock, pending.data());
        return matches ? pending.provenance() : null;
    }

    static CraftingChemistry.@Nullable Data placementChemistry(@Nullable Player player, Block placedBlock, @Nullable PendingVariantUse pending,
                                                               @Nullable ItemVariantData context, CraftingChemistry.@Nullable Data contextChemistry) {
        if (player != null) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            ItemVariantData mainData = DynamicVariantRegistry.variantUseData(mainHand);
            ItemVariantData offData = DynamicVariantRegistry.variantUseData(offHand);
            if (Block.byItem(mainHand.getItem()) == placedBlock || mainData != null && matchesPlacementSource(placedBlock, mainData)) {
                CraftingChemistry.Data chemistry = mainHand.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
                if (chemistry != null) return chemistry;
            }
            if (Block.byItem(offHand.getItem()) == placedBlock || offData != null && matchesPlacementSource(placedBlock, offData)) {
                CraftingChemistry.Data chemistry = offHand.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
                if (chemistry != null) return chemistry;
            }
        }

        if (context != null && matchesPlacementSource(placedBlock, context) && contextChemistry != null) return contextChemistry;
        if (pending == null) return null;
        boolean matches = Block.byItem(pending.sourceItem()) == placedBlock
            || pending.data() != null && matchesPlacementSource(placedBlock, pending.data());
        return matches ? pending.chemistry() : null;
    }

    static boolean hasHeritableVariant(Mob entity) {
        return entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()) != null
            || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get()) != null
            || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get()) != null
            || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get()) != null;
    }

    static CraftingChemistry.@Nullable Data breedChemistry(Mob parentAlpha, Mob parentBeta, @Nullable ItemStack childPick, ItemVariantData phenotype) {
        ItemStack fallback = childPick == null || childPick.isEmpty() ? parentAlpha.getPickResult() : childPick.copy();
        if (fallback == null || fallback.isEmpty()) fallback = parentBeta.getPickResult();
        if (fallback == null || fallback.isEmpty()) return null;
        ItemStack alpha = chemistryProxy(parentAlpha, fallback);
        ItemStack beta = chemistryProxy(parentBeta, fallback);
        if (alpha.isEmpty() || beta.isEmpty()) return null;
        CraftingChemistry.Synthesis synthesis = CraftingChemistry.synthesize(CraftingInput.of(2, 1, List.of(alpha, beta)));
        return synthesis == null ? null : CraftingChemistry.retarget(synthesis.data(), phenotype.stage(), phenotype.waxed(), phenotype.dyeColor(), phenotype.customColor());
    }

    static ItemStack chemistryProxy(Mob parent, ItemStack fallback) {
        ItemStack stack = parent.getPickResult();
        if (stack == null || stack.isEmpty()) stack = fallback.copy();
        else stack = stack.copy();
        ItemVariantData variant = parent.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (variant != null) {
            ItemStack transformed = DynamicVariantRegistry.transform(stack, variant.stage(), variant.waxed(), variant.dyeColor(), variant.customColor());
            if (!transformed.isEmpty()) stack = transformed;
        }

        CraftingChemistry.Data chemistry = parent.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
        if (chemistry != null) {
            stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), variant == null ? chemistry
                : CraftingChemistry.retarget(chemistry, variant.stage(), variant.waxed(), variant.dyeColor(), variant.customColor()));
        } else {
            CraftingChemistry.Synthesis synthesis = CraftingChemistry.synthesize(CraftingInput.of(1, 1, List.of(stack)));
            if (synthesis != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), synthesis.data());
        }

        return stack;
    }

    static OxidationStage randomNaturalStage(ServerLevel level) {
        double[] weights = PatinaRules.INSTANCE.naturalVariantStageWeights;
        double total = 0.0D;
        for (int index = 1; index < weights.length; index++) total += weights[index];
        double selected = level.getRandom().nextDouble() * total;
        for (int index = 1; index < weights.length; index++) {
            selected -= weights[index];
            if (selected <= 0.0D) return OxidationStage.byOrdinal(index);
        }
        return OxidationStage.OXIDIZED;
    }

    static void setEntityVariant(Entity entity, ItemVariantData data) {
        entity.setData(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get(), data);
        CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
        if (chemistry != null) {
            entity.setData(DynamicVariantRegistry.ENTITY_CHEMISTRY.get(),
                CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        }
    }

    @Nullable
    static ItemVariantData strongestVariant(@Nullable ItemVariantData first, @Nullable ItemVariantData second) {
        if (first == null) return second;
        if (second == null) return first;
        int comparison = Integer.compare(first.stage().ordinal(), second.stage().ordinal());
        if (comparison != 0) return comparison > 0 ? first : second;
        return first.waxed() && !second.waxed() ? second : first;
    }

    record VariantUseFrame(@Nullable ItemVariantData data, @Nullable VariantData blockData, VariantProvenance.@Nullable Data provenance,
                           CraftingChemistry.@Nullable Data chemistry, VariantGenetics.@Nullable Data genetics) {}

    record PendingVariantUse(long gameTime, Item sourceItem, @Nullable ItemVariantData data, VariantProvenance.@Nullable Data provenance,
                             CraftingChemistry.@Nullable Data chemistry) {}

    record PendingBlockReplacement(Block previousSource, ItemVariantData data) {}

    record PreparedBlockReplacement(BlockState state, VariantData data) {}

    record PendingToolTransformation(Block targetBlock, VariantData data, ItemStack tool, String operation) {}

    record PendingLightningStrike(long dueGameTime, BlockPos pos) {}

    record LightningCleanProtection(int lightningId, long expiresAt) {}

    record SaplingGroup(Block source, List<BlockPos> positions, List<@Nullable VariantData> variants) {}

    record PendingTreeGrowth(long dueGameTime, BlockPos min, BlockPos max, Set<Long> existingWood,
                                     Block saplingSource, List<BlockPos> saplingPositions,
                                     List<@Nullable VariantData> variants, long seed) {}

}
