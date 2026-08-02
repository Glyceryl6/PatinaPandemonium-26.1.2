package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantRuntime;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public interface PatinaOxidizable extends WeatheringCopper {

    VariantData patinaData();

    @Override
    default WeatherState getAge() {
        return patinaData().stage().weatherState();
    }

    @Override
    default Optional<BlockState> getNext(BlockState state) {
        return patinaData().waxed() ? Optional.empty() : VariantRuntime.next(state);
    }

}