package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.VariantData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class PatinaStairBlock extends StairBlock implements PatinaOxidizable {

    private final VariantData data;

    public PatinaStairBlock(BlockState baseState, BlockBehaviour.Properties properties, VariantData data) {
        super(baseState, properties);
        this.data = data;
    }

    @Override
    public VariantData patinaData() {
        return this.data;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !this.data.waxed() && this.data.stage().next() != null;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.data.waxed()) this.changeOverTime(state, level, pos, random);
    }

}