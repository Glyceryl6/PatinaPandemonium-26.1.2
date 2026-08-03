package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class PatinaBlock extends Block implements PatinaOxidizable {

    public PatinaBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.FULL;
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