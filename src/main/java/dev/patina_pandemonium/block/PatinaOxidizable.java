package dev.patina_pandemonium.block;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        return this.patinaCloneItemStack(level, pos, state);
    }

    default ItemStack patinaCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        PatinaVariantBlockEntity blockEntity = level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity variantBlockEntity ? variantBlockEntity : null;
        VariantData data = blockEntity == null ? VariantData.defaultFor(this.patinaForm()) : blockEntity.data();
        ItemStack stack = DynamicVariantRegistry.stack(data.normalized(this.patinaForm()));
        if (blockEntity == null) return stack;
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
        if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), CraftingChemistry.retarget(
            chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(blockEntity);
        if (provenance != null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
        return stack;
    }

    @Override
    default int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return patinaSourceState(state).getFlammability(level, pos, direction);
    }

    @Override
    default boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return patinaSourceState(state).isFlammable(level, pos, direction);
    }

    @Override
    default boolean onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter) {
        return patinaSourceState(state).onCaughtFire(level, pos, direction, igniter);
    }

    @Override
    default int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return patinaSourceState(state).getFireSpreadSpeed(level, pos, direction);
    }

    @Override
    default boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction direction) {
        return patinaSourceState(state).isFireSource(level, pos, direction);
    }

    @Override
    default boolean isBurning(BlockState state, BlockGetter level, BlockPos pos) {
        return patinaSourceState(state).isBurning(level, pos);
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

    private static BlockState patinaSourceState(BlockState state) {
        var sourceId = DynamicVariantRegistry.sourceId(state.getBlock());
        if (sourceId == null) return Blocks.AIR.defaultBlockState();
        Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
        return source == Blocks.AIR ? Blocks.AIR.defaultBlockState() : source.withPropertiesOf(state);
    }

    private static boolean setData(Level level, BlockPos pos, VariantData data) {
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) || blockEntity.data().equals(data)) return false;
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
        if (chemistry != null) DynamicVariantRegistry.setBlockEntityChemistry(blockEntity,
            CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        blockEntity.setData(data);
        return true;
    }

}
