package dev.patina_pandemonium.block;

import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class PatinaPressurePlateBlock extends PressurePlateBlock implements PatinaOxidizable {

    public PatinaPressurePlateBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.OAK, properties);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.PRESSURE_PLATE;
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