package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.block.PatinaBlock;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

import java.util.*;

import static dev.patina_pandemonium.event.PatinaGameplayEvents.*;

/** Event handlers grouped by gameplay responsibility. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantInteractionEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return;
        ItemStack held = player.getItemInHand(event.getHand());
        ItemVariantData heldData = DynamicVariantRegistry.itemData(held);
        VariantProvenance.Data heldProvenance = VariantProvenance.get(held);
        CraftingChemistry.Data heldChemistry = held.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        if (heldData == null && heldProvenance == null && heldChemistry == null) PENDING_USES.remove(player);
        else PENDING_USES.put(player, new PendingVariantUse(level.getGameTime(), held.getItem(), heldData, heldProvenance, heldChemistry));
        BlockPos pos = event.getPos();
        if (heldData != null && IGNITERS.contains(held.getItem()) && level instanceof ServerLevel serverLevel) {
            queueFireReplacement(serverLevel, pos, heldData);
            if (event.getFace() != null) {
                queueFireReplacement(serverLevel, pos.relative(event.getFace()), heldData);
            }
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        VariantData current = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        if (current == null) return;
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
        setVariantData(level, pos, target.get());
        if (level instanceof ServerLevel serverLevel) appendBlockCatalystHistory(serverLevel, pos, held);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (levelEvent >= 0) level.levelEvent(player, levelEvent, pos, 0);
        if (!player.getAbilities().instabuild) {
            if (held.is(Items.HONEYCOMB) || held.getItem() instanceof DyeItem) held.shrink(1);
            else held.hurtAndBreak(1, player, event.getHand());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (held.is(Items.HONEYCOMB)) VariantAdvancements.interaction(serverPlayer, VariantAdvancements.Metric.WAX_BLOCK);
            else if (held.canPerformAction(ItemAbilities.AXE_WAX_OFF) || held.canPerformAction(ItemAbilities.AXE_SCRAPE)) {
                VariantAdvancements.interaction(serverPlayer, VariantAdvancements.Metric.SCRAPE_BLOCK);
            }
        }
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !(event.getTarget() instanceof LivingEntity target) || target instanceof Player) return;
        ItemStack held = player.getItemInHand(event.getHand());
        ItemVariantData current = target.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemVariantData changed = null;
        SoundEvent sound = null;
        if (held.is(Items.HONEYCOMB) && (current == null || !current.waxed())) {
            changed = (current == null ? ItemVariantData.defaultData() : current).withWaxed(true);
            sound = SoundEvents.HONEYCOMB_WAX_ON;
            if (!player.getAbilities().instabuild) held.shrink(1);
        } else if (current != null && current.waxed() && held.canPerformAction(ItemAbilities.AXE_WAX_OFF)) {
            changed = current.withWaxed(false);
            sound = SoundEvents.AXE_WAX_OFF;
        } else if (current != null && held.canPerformAction(ItemAbilities.AXE_SCRAPE) && current.stage().previous() != null) {
            changed = current.withStage(current.stage().previous());
            sound = SoundEvents.AXE_SCRAPE;
        }

        if (changed == null) return;
        setEntityVariant(target, changed);
        level.playSound(null, target.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!player.getAbilities().instabuild && !held.is(Items.HONEYCOMB)) held.hurtAndBreak(1, player, event.getHand());
        if (player instanceof ServerPlayer serverPlayer) {
            VariantAdvancements.interaction(serverPlayer, held.is(Items.HONEYCOMB) ? VariantAdvancements.Metric.WAX_ENTITY : VariantAdvancements.Metric.SCRAPE_ENTITY);
        }
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() == ItemAbilities.AXE_SCRAPE || event.getItemAbility() == ItemAbilities.AXE_WAX_OFF) return;
        BlockState state = event.getState();
        if (event.getFinalState() != state) return;
        BlockState sourceState;
        if (state.getBlock() instanceof PatinaDelegatingBlock delegated) sourceState = delegated.sourceState(state);
        else if (state.getBlock() instanceof PatinaBlock patina) sourceState = patina.source().defaultBlockState();
        else return;
        VariantData data = variantData(event.getContext().getLevel(), event.getPos());
        if (data == null) return;
        BlockState modified = sourceState.getBlock().getToolModifiedState(
            sourceState, event.getContext(), event.getItemAbility(), event.isSimulated());
        if (modified == null || modified.equals(sourceState)) return;
        Block targetBlock = DynamicVariantRegistry.isNativeBlockEntitySource(modified.getBlock())
            ? modified.getBlock() : DynamicVariantRegistry.fullCarrier(modified.getBlock());
        if (targetBlock == null) return;
        BlockState target = targetBlock == modified.getBlock() ? modified
            : targetBlock instanceof PatinaDelegatingBlock ? targetBlock.withPropertiesOf(modified) : targetBlock.defaultBlockState();
        event.setFinalState(target);
        if (event.isSimulated() || !(event.getContext().getLevel() instanceof ServerLevel level)) return;
        VariantData targetData = new VariantData(BuiltInRegistries.BLOCK.getKey(modified.getBlock()), data.stage(), data.waxed(),
            VariantForm.FULL, data.dyeColor(), data.customColor());
        PENDING_TOOL_TRANSFORMATIONS.computeIfAbsent(level, _ -> new LinkedHashMap<>())
            .put(event.getPos().immutable(), new PendingToolTransformation(target.getBlock(), targetData, event.getContext().getItemInHand().copy()));
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity() instanceof Player placedByPlayer ? placedByPlayer : null;
        PendingVariantUse pending = player == null ? null : PENDING_USES.remove(player);
        if (pending != null && level.getGameTime() - pending.gameTime() > 1L) pending = null;
        ItemVariantData context = currentVariantUse();
        VariantProvenance.Data contextProvenance = currentProvenance();
        CraftingChemistry.Data contextChemistry = currentChemistryUse();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                BlockState placedState = snapshot.getCurrentState();
                ItemVariantData data = placementVariant(player, placedState.getBlock(), pending, context);
                VariantProvenance.Data provenance = placementProvenance(player, placedState.getBlock(), pending, context, contextProvenance);
                CraftingChemistry.Data chemistry = placementChemistry(player, placedState.getBlock(), pending, context, contextChemistry);
                if (data != null) replacePlacedBlock(level, snapshot.getPos(), placedState, data, provenance, chemistry);
                else attachPlacedHistory(level, snapshot.getPos(), provenance, chemistry);
            }
            return;
        }

        BlockState placedState = event.getPlacedBlock();
        ItemVariantData data = placementVariant(player, placedState.getBlock(), pending, context);
        VariantProvenance.Data provenance = placementProvenance(player, placedState.getBlock(), pending, context, contextProvenance);
        CraftingChemistry.Data chemistry = placementChemistry(player, placedState.getBlock(), pending, context, contextChemistry);
        if (data != null) replacePlacedBlock(level, event.getPos(), placedState, data, provenance, chemistry);
        else attachPlacedHistory(level, event.getPos(), provenance, chemistry);
    }

}