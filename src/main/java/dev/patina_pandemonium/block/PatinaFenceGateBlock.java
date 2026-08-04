package dev.patina_pandemonium.block;

import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

public class PatinaFenceGateBlock extends FenceGateBlock implements PatinaOxidizable {

    public PatinaFenceGateBlock(BlockBehaviour.Properties properties) {
        super(WoodType.OAK, properties);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.FENCE_GATE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.patinaRandomTick(level, pos, random);
    }

}