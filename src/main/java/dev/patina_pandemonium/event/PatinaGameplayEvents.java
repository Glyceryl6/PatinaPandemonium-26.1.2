package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.OxidationStage;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import dev.patina_pandemonium.registry.VariantRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Supplies server behavior that cannot be represented by generated resources. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class PatinaGameplayEvents {

    private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
    private static final Map<Player, PendingVariantUse> PENDING_USES = new WeakHashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, PendingBlockReplacement>> PENDING_REPLACEMENTS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<PendingLightningStrike>> PENDING_LIGHTNING_STRIKES = new WeakHashMap<>();

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
            if (dyeColor != null && current.dyeColor() != dyeColor) target = Optional.of(current.withDye(dyeColor));
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

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt lightning) || event.loadedFromDisk()) return;
        BlockPos strikePos = BlockPos.containing(lightning.getX(), lightning.getY() - 1.0E-6D, lightning.getZ());
        PENDING_LIGHTNING_STRIKES.computeIfAbsent(level, _ -> new ArrayList<>()).add(new PendingLightningStrike(level.getGameTime() + 1L, strikePos));
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
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        processFireReplacements(level);
        processLightningStrikes(level);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof PatinaOxidizable)
            || !(event.getBlockEntity() instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData data = blockEntity.data();
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
            if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(replacement.data());
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

    private static ItemVariantData heldVariant(Player player, Block placedBlock, @Nullable PendingVariantUse pending) {
        ItemVariantData mainHand = DynamicVariantRegistry.itemData(player.getMainHandItem());
        ItemVariantData offHand = DynamicVariantRegistry.itemData(player.getOffhandItem());
        if (mainHand != null && matchesPlacementSource(placedBlock, mainHand)) return mainHand;
        if (offHand != null && matchesPlacementSource(placedBlock, offHand)) return offHand;
        return pending != null && matchesPlacementSource(placedBlock, pending.data()) ? pending.data() : null;
    }

    private record PendingVariantUse(long gameTime, ItemVariantData data) {}

    private record PendingBlockReplacement(Block previousSource, ItemVariantData data) {}

    private record PreparedBlockReplacement(BlockState state, VariantData data) {}

    private record PendingLightningStrike(long dueGameTime, BlockPos pos) {}

}