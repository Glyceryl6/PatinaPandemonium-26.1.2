package dev.patinapandemonium.registry;

import dev.patinapandemonium.config.PatinaRules;

public enum VariantForm {

    FULL("", "full"),
    SLAB("_slab", "slab"),
    STAIRS("_stairs", "stairs"),
    WALL("_wall", "wall"),
    FENCE("_fence", "fence"),
    FENCE_GATE("_fence_gate", "fence_gate"),
    BUTTON("_button", "button"),
    PRESSURE_PLATE("_pressure_plate", "pressure_plate"),
    SIGN("_sign", "sign"),
    WALL_SIGN("_wall_sign", "wall_sign");

    private final String suffix;
    private final String id;

    VariantForm(String suffix, String id) {
        this.suffix = suffix;
        this.id = id;
    }

    public String suffix() {
        return suffix;
    }

    public String id() {
        return id;
    }

    public boolean hasItem() {
        return this != WALL_SIGN;
    }

    public boolean enabled(PatinaRules rules) {
        return switch (this) {
            case FULL -> true;
            case SLAB -> rules.slabs;
            case STAIRS -> rules.stairs;
            case WALL -> rules.walls;
            case FENCE -> rules.fences;
            case FENCE_GATE -> rules.fenceGates;
            case BUTTON -> rules.buttons;
            case PRESSURE_PLATE -> rules.pressurePlates;
            case SIGN, WALL_SIGN -> rules.signs;
        };
    }

}