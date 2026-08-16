package dev.patina_pandemonium.registry;

import dev.patina_pandemonium.block.PatinaOxidizable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/** Keeps the physical Patina workbench attached to the CraftingMenu it opened. */
public class CraftingWorkstationContext {

    private static final Map<Player, Entry> ACTIVE = new WeakHashMap<>();

    public static void capture(Player player, Level level, BlockPos pos, Block source) {
        if (level.isClientSide() || source != Blocks.CRAFTING_TABLE || !(player.containerMenu instanceof CraftingMenu)) return;
        ACTIVE.put(player, new Entry(level.dimension(), pos.immutable(), player.containerMenu.containerId));
    }

    public static ItemStack workstation(Player player) {
        Entry entry = ACTIVE.get(player);
        if (entry == null || !(player.containerMenu instanceof CraftingMenu) || player.containerMenu.containerId != entry.menuId()
            || !player.level().dimension().equals(entry.dimension()) || player.distanceToSqr(entry.pos().getX() + 0.5D, entry.pos().getY() + 0.5D, entry.pos().getZ() + 0.5D) > 64.0D) {
            ACTIVE.remove(player);
            return ItemStack.EMPTY;
        }

        Level level = player.level();
        BlockState state = level.getBlockState(entry.pos());
        var sourceId = DynamicVariantRegistry.sourceId(state.getBlock());
        if (sourceId == null || BuiltInRegistries.BLOCK.getValue(sourceId) != Blocks.CRAFTING_TABLE
            || !(state.getBlock() instanceof PatinaOxidizable oxidizable)) {
            ACTIVE.remove(player);
            return ItemStack.EMPTY;
        }
        return oxidizable.patinaCloneItemStack(level, entry.pos(), state);
    }

    private record Entry(ResourceKey<Level> dimension, BlockPos pos, int menuId) {}

}