package dev.patina_pandemonium.block;

import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class PatinaSlabBlock extends SlabBlock implements PatinaOxidizable {

    public PatinaSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.SLAB;
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