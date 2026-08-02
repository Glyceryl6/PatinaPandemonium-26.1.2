package dev.patinapandemonium.registry;

import net.minecraft.world.level.block.WeatheringCopper;

public enum OxidationStage {

    FRESH("fresh", WeatheringCopper.WeatherState.UNAFFECTED, 0xFFFFFF),
    EXPOSED("exposed", WeatheringCopper.WeatherState.EXPOSED, 0xD5AD83),
    WEATHERED("weathered", WeatheringCopper.WeatherState.WEATHERED, 0x82B590),
    OXIDIZED("oxidized", WeatheringCopper.WeatherState.OXIDIZED, 0x59AA9D);

    private final String id;
    private final WeatheringCopper.WeatherState weatherState;
    private final int fallbackColor;

    OxidationStage(String id, WeatheringCopper.WeatherState weatherState, int fallbackColor) {
        this.id = id;
        this.weatherState = weatherState;
        this.fallbackColor = fallbackColor;
    }

    public String id() {
        return id;
    }

    public WeatheringCopper.WeatherState weatherState() {
        return weatherState;
    }

    public int fallbackColor() {
        return fallbackColor;
    }

    public OxidationStage next() {
        return this == FRESH ? EXPOSED : this == EXPOSED ? WEATHERED : this == WEATHERED ? OXIDIZED : null;
    }

}