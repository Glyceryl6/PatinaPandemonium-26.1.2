package dev.patina_pandemonium.block;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface PatinaOxidizable extends EntityBlock, IBlockExtension {

    VariantForm patinaForm();

    @Override
    default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatinaVariantBlockEntity(pos, state);
    }

    @Override
    default ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        VariantData data = level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity
                ? blockEntity.data() : VariantData.defaultFor(this.patinaForm());
        return DynamicVariantRegistry.stack(data.normalized(this.patinaForm()));
    }

    @Override
    @Nullable
    default BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof PatinaVariantBlockEntity blockEntity)) return null;
        VariantData current = blockEntity.data();
        Optional<VariantData> target;
        if (itemAbility == ItemAbilities.AXE_SCRAPE) {
            target = current.waxed() ? Optional.empty() : VariantRuntime.previous(current);
        } else if (itemAbility == ItemAbilities.AXE_WAX_OFF) {
            target = VariantRuntime.unwaxed(current);
        } else {
            return null;
        }
        if (target.isEmpty()) return null;
        if (!simulate) setLinkedData(context.getLevel(), context.getClickedPos(), target.get());
        return state;
    }

    default void patinaRandomTick(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return;
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData data = blockEntity.data();
        if (data.waxed() || data.stage().next() == null || random.nextDouble() >= PatinaRules.INSTANCE.oxidationAttemptChance) return;

        int sameStage = 0;
        int laterStage = 0;
        int currentStage = data.stage().ordinal();
        for (BlockPos neighborPos : BlockPos.withinManhattan(pos, 4, 4, 4)) {
            if (neighborPos.distManhattan(pos) > 4) break;
            if (neighborPos.equals(pos) || !level.hasChunkAt(neighborPos)
                || !(level.getBlockEntity(neighborPos) instanceof PatinaVariantBlockEntity neighborEntity)) continue;
            VariantData neighbor = neighborEntity.data();
            if (neighbor.waxed()) continue;
            int neighborStage = neighbor.stage().ordinal();
            if (neighborStage < currentStage) return;
            if (neighborStage == currentStage) sameStage++;
            else laterStage++;
        }

        double ratio = (double) (laterStage + 1) / (laterStage + sameStage + 1);
        double stageModifier = data.stage() == OxidationStage.FRESH ? 0.75D : 1.0D;
        if (random.nextDouble() >= ratio * ratio * stageModifier) return;
        VariantRuntime.next(data).ifPresent(next -> setLinkedData(level, pos, next));
    }

    static boolean setLinkedData(Level level, BlockPos pos, VariantData data) {
        boolean changed = setData(level, pos, data);
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return changed;
        DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(state.getBlock()) && otherState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && otherState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half) changed |= setData(level, otherPos, data);
        return changed;
    }

    private static boolean setData(Level level, BlockPos pos, VariantData data) {
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) || blockEntity.data().equals(data)) return false;
        blockEntity.setData(data);
        return true;
    }

}