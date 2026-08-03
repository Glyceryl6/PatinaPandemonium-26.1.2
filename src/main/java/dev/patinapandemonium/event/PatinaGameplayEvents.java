package dev.patinapandemonium.event;

import dev.patinapandemonium.block.PatinaDelegatingBlock;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.ItemVariantData;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Supplies server behavior that cannot be represented by generated resources. */
public class PatinaGameplayEvents {

    private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
    private static final Map<Player, PendingVariantUse> PENDING_USES = new WeakHashMap<>();

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return;
        ItemStack held = player.getItemInHand(event.getHand());
        ItemVariantData heldData = DynamicVariantRegistry.itemData(held);
        if (heldData == null) PENDING_USES.remove(player);
        else PENDING_USES.put(player, new PendingVariantUse(level.getGameTime(), heldData));
        BlockPos pos = event.getPos();
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
                if (data != null) replacePlacedBlock(level, snapshot.getPos(), placedState, data);
            }
            return;
        }
        BlockState placedState = event.getPlacedBlock();
        ItemVariantData data = heldVariant(player, placedState.getBlock(), pending);
        if (data != null) replacePlacedBlock(level, event.getPos(), placedState, data);
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemVariantData data = DynamicVariantRegistry.itemData(event.getItemStack());
        if (data == null) return;
        event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.variant_stage", Component.translatable(data.stageKey()))
            .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.variant_dye", Component.translatable(data.dyeKey()))
            .withStyle(ChatFormatting.GRAY));
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof PatinaOxidizable)
            || !(event.getBlockEntity() instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData data = blockEntity.data();
        if (event.getState().getBlock() instanceof PatinaDelegatingBlock delegated) {
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (DynamicVariantRegistry.supportsFabrication(stack)) {
                    drop.setItem(DynamicVariantRegistry.fabricate(
                        stack, VariantForm.FULL, data.stage(), data.waxed(), data.dyeColor(), stack.getCount()));
                }
            }
            if (!event.getDrops().isEmpty() || delegated.source().asItem() == Items.AIR) return;
            event.getDrops().add(new ItemEntity(
                event.getLevel(), event.getPos().getX() + 0.5D, event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D,
                DynamicVariantRegistry.fabricate(
                    new ItemStack(delegated.source()), VariantForm.FULL, data.stage(), data.waxed(), data.dyeColor(), 1)));
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

    private static void replacePlacedBlock(ServerLevel level, BlockPos pos, BlockState placedState, ItemVariantData data) {
        Block source = placedState.getBlock();
        Block carrier = DynamicVariantRegistry.delegatedCarrier(source);
        if (carrier == null) return;
        BlockState replacement = carrier.withPropertiesOf(placedState);
        level.setBlock(pos, replacement, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) {
            blockEntity.setData(data.forBlock(BuiltInRegistries.BLOCK.getKey(source)));
        }
    }

    private static boolean matchesPlacementSource(Block source, ItemVariantData data) {
        Item sourceItem = BuiltInRegistries.ITEM.getValue(data.sourceId());
        return Block.byItem(sourceItem) == source || source instanceof BaseFireBlock && IGNITERS.contains(sourceItem);
    }

    private static ItemVariantData heldVariant(Player player, Block placedBlock, PendingVariantUse pending) {
        ItemVariantData mainHand = DynamicVariantRegistry.itemData(player.getMainHandItem());
        ItemVariantData offHand = DynamicVariantRegistry.itemData(player.getOffhandItem());
        if (mainHand != null && matchesPlacementSource(placedBlock, mainHand)) return mainHand;
        if (offHand != null && matchesPlacementSource(placedBlock, offHand)) return offHand;
        return pending != null && matchesPlacementSource(placedBlock, pending.data()) ? pending.data() : null;
    }

    private record PendingVariantUse(long gameTime, ItemVariantData data) {}
}
