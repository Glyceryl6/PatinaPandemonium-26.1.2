package dev.patinapandemonium.event;

import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Optional;

/**
 * Supplies the small amount of server behavior that previously depended on generated data packs.
 */
public class PatinaGameplayEvents {

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity)) return;
        ItemStack held = player.getItemInHand(event.getHand());
        VariantData current = blockEntity.data();
        Optional<VariantData> target = Optional.empty();
        SoundEvent sound = null;
        if (held.getItem() instanceof DyeItem dyeItem) {
            DyeColor dyeColor = dyeItem.components().get(DataComponents.DYE);
            if (dyeColor != null && current.dyeColor() != dyeColor) {
                target = Optional.of(current.withDye(dyeColor));
            }

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
        if ((held.is(Items.HONEYCOMB)
                || held.getItem() instanceof DyeItem)
                && !player.getAbilities().instabuild) held.shrink(1);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof PatinaOxidizable)
                || !(event.getBlockEntity() instanceof PatinaVariantBlockEntity blockEntity)) return;
        event.getDrops().clear();
        BlockPos pos = event.getPos();
        int count = event.getState().getBlock() instanceof SlabBlock
                && event.getState().getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        event.getDrops().add(new ItemEntity(
                event.getLevel(),
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                DynamicVariantRegistry.stack(blockEntity.data(), count)));
    }

}