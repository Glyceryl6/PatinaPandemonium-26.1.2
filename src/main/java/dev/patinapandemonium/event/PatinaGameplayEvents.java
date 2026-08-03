package dev.patinapandemonium.event;

import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Optional;

/** Supplies the small amount of server behavior that previously depended on generated data packs. */
public class PatinaGameplayEvents {

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }

        ItemStack held = player.getItemInHand(event.getHand());
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Optional<BlockState> target = Optional.empty();
        SoundEvent sound = null;

        if (held.is(Items.HONEYCOMB)) {
            target = VariantRuntime.waxed(state);
            sound = SoundEvents.HONEYCOMB_WAX_ON;
        } else if (held.is(ItemTags.AXES)) {
            target = VariantRuntime.unwaxed(state);
            sound = target.isPresent() ? SoundEvents.AXE_WAX_OFF : SoundEvents.AXE_SCRAPE;
            if (target.isEmpty()) {
                target = VariantRuntime.previous(state);
            }
        }

        if (target.isEmpty()) {
            return;
        }

        level.setBlockAndUpdate(pos, target.get());
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (held.is(Items.HONEYCOMB) && !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof PatinaOxidizable) || !event.getDrops().isEmpty()) {
            return;
        }

        Block drop = VariantRuntime.drop(event.getState().getBlock());
        if (drop.asItem() == Items.AIR) {
            return;
        }

        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(
            event.getLevel(),
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D,
            new ItemStack(drop)));
    }

}