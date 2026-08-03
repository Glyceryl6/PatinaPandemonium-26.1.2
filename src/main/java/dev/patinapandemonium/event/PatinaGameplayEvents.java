package dev.patinapandemonium.event;

import dev.patinapandemonium.block.PatinaDelegatingBlock;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.ItemVariantData;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
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
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Supplies server behavior that cannot be represented by generated resources. */
public class PatinaGameplayEvents {

    private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
    private static final Map<Player, PendingVariantUse> PENDING_USES = new WeakHashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, PendingBlockReplacement>> PENDING_REPLACEMENTS = new WeakHashMap<>();

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
        if (held.getItem() instanceof DyeItem dyeItem) {
            DyeColor dyeColor = dyeItem.components().get(DataComponents.DYE);
            if (dyeColor != null && current.dyeColor() != dyeColor) target = Optional.of(current.withDye(dyeColor));
            sound = SoundEvents.DYE_USE;
        } else if (held.is(Items.HONEYCOMB)) {
            target = VariantRuntime.waxed(current);
            sound = SoundEvents.HONEYCOMB_WAX_ON;
        } else if (held.is(ItemTags.AXES)) {
            target = VariantRuntime.unwaxed(current);
            sound = target.isPresent() ? SoundEvents.AXE_WAX_OFF : SoundEvents.AXE_SCRAPE;
            if (target.isEmpty()) target = VariantRuntime.previous(current);
        }

        if (target.isEmpty()) return;
        blockEntity.setData(target.get());
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if ((held.is(Items.HONEYCOMB) || held.getItem() instanceof DyeItem) && !player.getAbilities().instabuild) held.shrink(1);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) return;
        PendingVariantUse pending = PENDING_USES.remove(player);
        if (pending != null && level.getGameTime() - pending.gameTime() > 1L) pending = null;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                BlockState placedState = snapshot.getCurrentState();
                ItemVariantData data = heldVariant(player, placedState.getBlock(), pending);
                if (data != null) queueReplacement(level, snapshot.getPos(), placedState.getBlock(), data);
            }
            return;
        }

        BlockState placedState = event.getPlacedBlock();
        ItemVariantData data = heldVariant(player, placedState.getBlock(), pending);
        if (data != null) queueReplacement(level, event.getPos(), placedState.getBlock(), data);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LinkedHashMap<BlockPos, PendingBlockReplacement> pending = PENDING_REPLACEMENTS.remove(level);
        if (pending == null) return;
        LinkedHashMap<BlockPos, PreparedBlockReplacement> prepared = new LinkedHashMap<>();
        pending.forEach((pos, replacement) -> {
            BlockState state = level.getBlockState(pos);
            Block expected = replacement.expectedSource();
            if (expected != null && state.getBlock() != expected) return;
            if (expected == null && (!(state.getBlock() instanceof BaseFireBlock)
                || replacement.previousSource() instanceof BaseFireBlock)) return;
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

    private static void queueReplacement(ServerLevel level, BlockPos pos, Block expectedSource, ItemVariantData data) {
        PENDING_REPLACEMENTS.computeIfAbsent(level, _ -> new LinkedHashMap<>())
            .put(pos.immutable(), new PendingBlockReplacement(expectedSource, null, data));
    }

    private static void queueFireReplacement(ServerLevel level, BlockPos pos, ItemVariantData data) {
        Block previousSource = level.getBlockState(pos).getBlock();
        PENDING_REPLACEMENTS.computeIfAbsent(level, _ -> new LinkedHashMap<>())
            .put(pos.immutable(), new PendingBlockReplacement(null, previousSource, data));
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

    private record PendingBlockReplacement(@Nullable Block expectedSource, @Nullable Block previousSource, ItemVariantData data) {}

    private record PreparedBlockReplacement(BlockState state, VariantData data) {}

}