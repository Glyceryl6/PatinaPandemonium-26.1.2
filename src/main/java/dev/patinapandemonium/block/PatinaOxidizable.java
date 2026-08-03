package dev.patinapandemonium.block;

import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

public interface PatinaOxidizable extends EntityBlock, IBlockExtension {

    VariantForm patinaForm();

    @Override
    default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatinaVariantBlockEntity(pos, state);
    }

    @Override
    default ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity
            ? DynamicVariantRegistry.stack(blockEntity.data())
            : DynamicVariantRegistry.stack(VariantData.defaultFor(this.patinaForm()));
    }

    default void patinaRandomTick(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity)) return;
        VariantData data = blockEntity.data();
        if (data.waxed() || data.stage().next() == null || random.nextDouble() >= PatinaRules.INSTANCE.oxidationAttemptChance) return;
        VariantRuntime.next(data).ifPresent(blockEntity::setData);
    }

}