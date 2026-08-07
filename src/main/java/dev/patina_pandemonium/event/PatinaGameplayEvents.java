package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.PatinaBlock;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** Supplies server behavior that cannot be represented by generated resources. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class PatinaGameplayEvents {

    private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
    private static final Map<Player, PendingVariantUse> PENDING_USES = new WeakHashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, PendingBlockReplacement>> PENDING_REPLACEMENTS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<PendingLightningStrike>> PENDING_LIGHTNING_STRIKES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<PendingTreeGrowth>> PENDING_TREE_GROWTHS = new WeakHashMap<>();
    private static final Map<Player, Integer> INVENTORY_OXIDATION_CURSORS = new WeakHashMap<>();
    private static final ThreadLocal<ArrayDeque<VariantUseFrame>> VARIANT_USE_CONTEXT = new ThreadLocal<>();

    public static void beginVariantUse(ItemStack stack) {
        pushVariantUse(DynamicVariantRegistry.variantUseData(stack));
    }

    public static void beginVariantUse(@Nullable VariantData data) {
        if (data == null) {
            pushVariantUse(null);
            return;
        }
        Item sourceItem = BuiltInRegistries.BLOCK.getValue(data.sourceId()).asItem();
        Identifier sourceId = sourceItem == Items.AIR ? data.sourceId() : BuiltInRegistries.ITEM.getKey(sourceItem);
        pushVariantUse(new ItemVariantData(sourceId, data.stage(), data.waxed(), data.dyeColor(), sourceId));
    }

    private static void pushVariantUse(@Nullable ItemVariantData data) {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) {
            contexts = new ArrayDeque<>();
            VARIANT_USE_CONTEXT.set(contexts);
        }

        contexts.push(new VariantUseFrame(data));
    }

    public static void endVariantUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return;
        if (!contexts.isEmpty()) contexts.pop();
        if (contexts.isEmpty()) VARIANT_USE_CONTEXT.remove();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return;
        ItemStack held = player.getItemInHand(event.getHand());
        ItemVariantData heldData = DynamicVariantRegistry.itemData(held);
        if (heldData == null) PENDING_USES.remove(player);
        else PENDING_USES.put(player, new PendingVariantUse(level.getGameTime(), heldData));
        BlockPos pos = event.getPos();
        if (heldData != null && IGNITERS.contains(held.getItem()) && level instanceof ServerLevel serverLevel) {
            queueFireReplacement(serverLevel, pos, heldData);
            if (event.getFace() != null) {
                queueFireReplacement(serverLevel, pos.relative(event.getFace()), heldData);
            }
        }

        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData current = blockEntity.data();
        Optional<VariantData> target = Optional.empty();
        SoundEvent sound = null;
        int levelEvent = -1;
        if (held.getItem() instanceof DyeItem dyeItem) {
            DyeColor dyeColor = dyeItem.components().get(DataComponents.DYE);
            if (dyeColor != null && current.dyeColor() != dyeColor) {
                target = Optional.of(current.withDye(dyeColor));
            }

            sound = SoundEvents.DYE_USE;
        } else if (held.is(Items.HONEYCOMB)) {
            target = VariantRuntime.waxed(current);
            sound = SoundEvents.HONEYCOMB_WAX_ON;
        } else if (current.waxed() && held.canPerformAction(ItemAbilities.AXE_WAX_OFF)) {
            target = VariantRuntime.unwaxed(current);
            sound = SoundEvents.AXE_WAX_OFF;
            levelEvent = 3004;
        } else if (held.canPerformAction(ItemAbilities.AXE_SCRAPE)) {
            target = VariantRuntime.previous(current);
            sound = SoundEvents.AXE_SCRAPE;
            levelEvent = 3005;
        }

        if (target.isEmpty()) return;
        PatinaOxidizable.setLinkedData(level, pos, target.get());
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (levelEvent >= 0) level.levelEvent(player, levelEvent, pos, 0);
        if ((held.is(Items.HONEYCOMB) || held.getItem() instanceof DyeItem) && !player.getAbilities().instabuild) held.shrink(1);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBonemeal(BonemealEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.isValidBonemealTarget()) return;
        ItemVariantData data = DynamicVariantRegistry.variantUseData(event.getStack());
        if (data == null || data.stage() == OxidationStage.FRESH
            || BuiltInRegistries.ITEM.getValue(data.sourceId()) != Items.BONE_MEAL) return;
        if (level.getRandom().nextDouble() < PatinaRules.INSTANCE.bonemealSuccessChances[data.stage().ordinal()]) return;
        Player player = event.getPlayer();
        if (player == null || !player.getAbilities().instabuild) event.getStack().shrink(1);
        event.setSuccessful(true);
    }

    @SubscribeEvent
    public static void onBlockGrowFeature(BlockGrowFeatureEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SaplingGroup group = saplingGroup(level, event.getPos());
        if (group == null || group.variants().stream().allMatch(Objects::isNull)) return;
        PatinaRules rules = PatinaRules.INSTANCE;
        BlockPos center = group.positions().getFirst();
        BlockPos min = center.offset(-rules.treeScanHorizontalRadius, -rules.treeScanBelow, -rules.treeScanHorizontalRadius);
        BlockPos max = center.offset(rules.treeScanHorizontalRadius, rules.treeScanHeight, rules.treeScanHorizontalRadius);
        Set<Long> existingWood = new HashSet<>();
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(candidate);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                existingWood.add(candidate.asLong());
            }
        }

        long seed = event.getRandom().nextLong() ^ center.asLong() ^ Long.rotateLeft(level.getGameTime(), 17);
        PENDING_TREE_GROWTHS.computeIfAbsent(level, _ -> new ArrayList<>())
            .add(new PendingTreeGrowth(level.getGameTime() + 1L, min, max, existingWood, group.source(),
                group.positions(), group.variants(), seed));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) return;
        Entity entity = event.getEntity();
        if (entity instanceof LightningBolt lightning) {
            BlockPos strikePos = BlockPos.containing(lightning.getX(), lightning.getY() - 1.0E-6D, lightning.getZ());
            PENDING_LIGHTNING_STRIKES.computeIfAbsent(level, _ -> new ArrayList<>())
                .add(new PendingLightningStrike(level.getGameTime() + 1L, strikePos));
            return;
        }

        ItemVariantData data = currentVariantUse();
        if (data != null && !(entity instanceof Player) && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            entity.setData(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get(), data);
        }
    }

    @SubscribeEvent
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return;
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()) != null
            || !level.isRainingAt(entity.blockPosition()) || level.getRandom().nextDouble() >= PatinaRules.INSTANCE.naturalVariantSpawnChance) return;
        ItemVariantData base = ItemVariantData.defaultData();
        entity.setData(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get(), new ItemVariantData(
            base.sourceId(), randomNaturalStage(level), false, null, base.modelId()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        if (entity instanceof Player player) {
            boolean inventoryChanged = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack cleaned = DynamicVariantRegistry.cleanOxidationCopy(player.getInventory().getItem(slot));
                if (cleaned.isEmpty()) continue;
                player.getInventory().setItem(slot, cleaned);
                inventoryChanged = true;
            }

            if (inventoryChanged) player.getInventory().setChanged();
        }

        ItemVariantData entityData = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (entityData == null || entityData.waxed() || entityData.stage() == OxidationStage.FRESH) return;
        entity.removeData(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel) || event.getInflictedDamage() <= 0.0F
            || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        Entity directSource = event.getSource().getDirectEntity();
        ItemStack weapon = attacker.getMainHandItem();
        boolean directAttack = directSource == null || directSource == attacker;
        ItemVariantData weaponData = directAttack && (weapon.has(DataComponents.WEAPON) || weapon.has(DataComponents.TOOL))
            ? DynamicVariantRegistry.variantUseData(weapon) : null;
        ItemVariantData data = strongestVariant(
            attacker.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()),
            strongestVariant(
                directSource == null ? null : directSource.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()),
                weaponData));
        if (data == null || data.stage() == OxidationStage.FRESH) return;
        int stage = data.stage().ordinal();
        double chance = PatinaRules.INSTANCE.tetanusChances[stage]
            * (data.waxed() ? PatinaRules.INSTANCE.waxTetanusMultiplier : 1.0D);
        if (attacker.getRandom().nextDouble() >= chance) return;
        event.getEntity().addEffect(new MobEffectInstance(
            DynamicVariantRegistry.TETANUS, PatinaRules.INSTANCE.tetanusDurations[stage], Math.max(0, stage - 2)));
    }

    @SubscribeEvent
    public static void onLivingUseItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        ItemStack consumed = event.getItem();
        ItemVariantData data = DynamicVariantRegistry.variantUseData(consumed);
        if (data == null || data.stage() == OxidationStage.FRESH || !consumed.has(DataComponents.FOOD)) return;
        int stage = data.stage().ordinal();
        double chance = PatinaRules.INSTANCE.foodPoisonChances[stage]
            * (data.waxed() ? PatinaRules.INSTANCE.waxFoodRiskMultiplier : 1.0D);
        if (event.getEntity().getRandom().nextDouble() >= chance) return;
        event.getEntity().addEffect(new MobEffectInstance(
            MobEffects.POISON, PatinaRules.INSTANCE.foodPoisonDurations[stage], Math.max(0, stage - 2)));
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        ItemVariantData data = event.getEntity().getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (data == null) return;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack transformed = DynamicVariantRegistry.transform(
                drop.getItem(), data.stage(), data.waxed(), data.dyeColor());
            if (!transformed.isEmpty()) drop.setItem(transformed);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) return;
        PendingVariantUse pending = PENDING_USES.remove(player);
        if (pending != null && level.getGameTime() - pending.gameTime() > 1L) pending = null;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                BlockState placedState = snapshot.getCurrentState();
                ItemVariantData data = heldVariant(player, placedState.getBlock(), pending);
                if (data != null) replacePlacedBlock(level, snapshot.getPos(), placedState, data);
            }
            return;
        }

        BlockState placedState = event.getPlacedBlock();
        ItemVariantData data = heldVariant(player, placedState.getBlock(), pending);
        if (data != null) replacePlacedBlock(level, event.getPos(), placedState, data);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        PatinaRules rules = PatinaRules.INSTANCE;
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)
            || player.tickCount % rules.inventoryOxidationInterval != 0
            || (!rules.inventoryOxidationAffectsCreative && player.getAbilities().instabuild)) return;
        BlockPos exposurePos = player.blockPosition().above();
        if ((rules.inventoryOxidationRequiresSky && !level.canSeeSky(exposurePos))
            || (rules.inventoryOxidationRequiresRain && !level.isRainingAt(exposurePos))) return;
        int size = player.getInventory().getContainerSize();
        if (size <= 0) return;
        int cursor = Math.floorMod(INVENTORY_OXIDATION_CURSORS.getOrDefault(player, 0), size);
        INVENTORY_OXIDATION_CURSORS.put(player, (cursor + 1) % size);
        if (level.getRandom().nextDouble() >= rules.inventoryOxidationAttemptChance) return;
        ItemStack oxidized = DynamicVariantRegistry.oxidizedCopy(player.getInventory().getItem(cursor));
        if (oxidized.isEmpty()) return;
        player.getInventory().setItem(cursor, oxidized);
        player.getInventory().setChanged();
        serverPlayer.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        processFireReplacements(level);
        processLightningStrikes(level);
        processTreeGrowths(level);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof PatinaOxidizable)
            || !(event.getBlockEntity() instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData data = blockEntity.data();
        Block source = sourceBlock(event.getState().getBlock());
        if (source.defaultBlockState().is(BlockTags.LEAVES)) {
            BlockState sourceState = event.getState().getBlock() instanceof PatinaDelegatingBlock delegated
                ? delegated.sourceState(event.getState()) : source.defaultBlockState();
            List<ItemStack> sourceDrops = Block.getDrops(
                sourceState, event.getLevel(), event.getPos(), null, event.getBreaker(), event.getTool());
            event.getDrops().clear();
            for (ItemStack stack : sourceDrops) {
                ItemStack transformed = DynamicVariantRegistry.transform(stack, data.stage(), data.waxed(), data.dyeColor());
                if (transformed.isEmpty()) transformed = stack;
                event.getDrops().add(new ItemEntity(event.getLevel(), event.getPos().getX() + 0.5D,
                    event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D, transformed));
            }
            return;
        }

        if (event.getState().getBlock() instanceof PatinaDelegatingBlock delegated) {
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (DynamicVariantRegistry.supportsFabrication(stack)) {
                    drop.setItem(DynamicVariantRegistry.fabricate(stack, VariantForm.FULL, data.stage(), data.waxed(), data.dyeColor(), stack.getCount()));
                }
            }

            if (!event.getDrops().isEmpty() || delegated.source().asItem() == Items.AIR) return;
            event.getDrops().add(new ItemEntity(event.getLevel(), event.getPos().getX() + 0.5D, event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D,
                    DynamicVariantRegistry.fabricate(new ItemStack(delegated.source()), VariantForm.FULL, data.stage(), data.waxed(), data.dyeColor(), 1)));
            return;
        }

        event.getDrops().clear();
        BlockPos pos = event.getPos();
        int count = event.getState().getBlock() instanceof SlabBlock
            && event.getState().getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        event.getDrops().add(new ItemEntity(
            event.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
            DynamicVariantRegistry.stack(data, count)));
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

    private static void processTreeGrowths(ServerLevel level) {
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
    private static SaplingGroup saplingGroup(ServerLevel level, BlockPos pos) {
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
    private static VariantData variantData(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity ? blockEntity.data() : null;
    }

    private static Block sourceBlock(Block block) {
        if (block instanceof PatinaDelegatingBlock delegated) return delegated.source();
        if (block instanceof PatinaBlock patina) return patina.source();
        return block;
    }

    private static OxidationStage randomStage(RandomSource random, double[] weights, boolean excludeFresh) {
        int first = excludeFresh ? 1 : 0;
        double total = 0.0D;
        for (int index = first; index < weights.length; index++) total += weights[index];
        if (total <= 0.0D) return excludeFresh ? OxidationStage.EXPOSED : OxidationStage.FRESH;
        double selected = random.nextDouble() * total;
        for (int index = first; index < weights.length; index++) {
            selected -= weights[index];
            if (selected <= 0.0D) {
                return OxidationStage.byOrdinal(index);
            }
        }

        return OxidationStage.OXIDIZED;
    }

    private static void processFireReplacements(ServerLevel level) {
        LinkedHashMap<BlockPos, PendingBlockReplacement> pending = PENDING_REPLACEMENTS.remove(level);
        if (pending == null) return;
        LinkedHashMap<BlockPos, PreparedBlockReplacement> prepared = new LinkedHashMap<>();
        pending.forEach((pos, replacement) -> {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BaseFireBlock) || replacement.previousSource() instanceof BaseFireBlock) return;
            Block source = state.getBlock();
            Block carrier = DynamicVariantRegistry.delegatedCarrier(source);
            if (carrier == null) return;
            prepared.put(pos, new PreparedBlockReplacement(
                carrier.withPropertiesOf(state),
                replacement.data().forBlock(BuiltInRegistries.BLOCK.getKey(source))));
        });

        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        prepared.forEach((pos, replacement) -> {
            level.setBlock(pos, replacement.state(), flags);
            if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) {
                blockEntity.setData(replacement.data());
            }
        });

        prepared.forEach((pos, replacement) -> level.updateNeighborsAt(pos, replacement.state().getBlock()));
    }

    private static void processLightningStrikes(ServerLevel level) {
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

    private static void cleanPatinaOnLightningStrike(ServerLevel level, BlockPos strikePos) {
        if (!(level.getBlockEntity(strikePos) instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData struckData = blockEntity.data();
        if (!struckData.waxed() && struckData.stage() != OxidationStage.FRESH) {
            PatinaOxidizable.setLinkedData(level, strikePos, struckData.withStage(OxidationStage.FRESH));
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
    private static BlockPos cleanRandomNearbyPatina(ServerLevel level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.randomInCube(level.getRandom(), 10, pos, 1)) {
            if (!(level.getBlockEntity(candidate) instanceof PatinaVariantBlockEntity blockEntity)) continue;
            VariantData data = blockEntity.data();
            if (data.waxed()) continue;
            Optional<VariantData> previous = VariantRuntime.previous(data);
            if (previous.isEmpty()) continue;
            PatinaOxidizable.setLinkedData(level, candidate, previous.get());
            level.levelEvent(3002, candidate, -1);
            return candidate.immutable();
        }

        return null;
    }

    private static void replacePlacedBlock(ServerLevel level, BlockPos pos, BlockState state, ItemVariantData data) {
        Block carrier = DynamicVariantRegistry.delegatedCarrier(state.getBlock());
        if (carrier == null) return;
        VariantData variant = data.forBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        BlockState target = carrier.withPropertiesOf(state);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        level.setBlock(pos, target, flags);
        if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(variant);
        level.updateNeighborsAt(pos, carrier);
    }

    private static void queueFireReplacement(ServerLevel level, BlockPos pos, ItemVariantData data) {
        Block previousSource = level.getBlockState(pos).getBlock();
        PENDING_REPLACEMENTS.computeIfAbsent(level, _ -> new LinkedHashMap<>())
            .put(pos.immutable(), new PendingBlockReplacement(previousSource, data));
    }

    private static boolean matchesPlacementSource(Block source, ItemVariantData data) {
        Item sourceItem = BuiltInRegistries.ITEM.getValue(data.sourceId());
        return Block.byItem(sourceItem) == source || source instanceof BaseFireBlock && IGNITERS.contains(sourceItem);
    }

    @Nullable
    private static ItemVariantData currentVariantUse() {
        ArrayDeque<VariantUseFrame> contexts = VARIANT_USE_CONTEXT.get();
        if (contexts == null) return null;
        for (VariantUseFrame frame : contexts) {
            if (frame.data() != null) return frame.data();
        }
        return null;
    }

    private static ItemVariantData heldVariant(Player player, Block placedBlock, @Nullable PendingVariantUse pending) {
        ItemVariantData mainHand = DynamicVariantRegistry.itemData(player.getMainHandItem());
        ItemVariantData offHand = DynamicVariantRegistry.itemData(player.getOffhandItem());
        if (mainHand != null && matchesPlacementSource(placedBlock, mainHand)) return mainHand;
        if (offHand != null && matchesPlacementSource(placedBlock, offHand)) return offHand;
        return pending != null && DynamicVariantRegistry.delegatedCarrier(placedBlock) != null ? pending.data() : null;
    }

    private static OxidationStage randomNaturalStage(ServerLevel level) {
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

    @Nullable
    private static ItemVariantData strongestVariant(@Nullable ItemVariantData first, @Nullable ItemVariantData second) {
        if (first == null) return second;
        if (second == null) return first;
        int comparison = Integer.compare(first.stage().ordinal(), second.stage().ordinal());
        if (comparison != 0) return comparison > 0 ? first : second;
        return first.waxed() && !second.waxed() ? second : first;
    }

    private record VariantUseFrame(@Nullable ItemVariantData data) {}

    private record PendingVariantUse(long gameTime, ItemVariantData data) {}

    private record PendingBlockReplacement(Block previousSource, ItemVariantData data) {}

    private record PreparedBlockReplacement(BlockState state, VariantData data) {}

    private record PendingLightningStrike(long dueGameTime, BlockPos pos) {}

    private record SaplingGroup(Block source, List<BlockPos> positions, List<@Nullable VariantData> variants) {}

    private record PendingTreeGrowth(long dueGameTime, BlockPos min, BlockPos max, Set<Long> existingWood,
                                     Block saplingSource, List<BlockPos> saplingPositions,
                                     List<@Nullable VariantData> variants, long seed) {}

}