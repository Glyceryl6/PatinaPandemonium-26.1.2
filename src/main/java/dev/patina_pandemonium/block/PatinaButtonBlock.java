package dev.patina_pandemonium.block;

import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class PatinaButtonBlock extends ButtonBlock implements PatinaOxidizable {

    public PatinaButtonBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.OAK, 30, properties);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.BUTTON;
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