package dev.patinapandemonium.registry;

import dev.patinapandemonium.config.PatinaRules;

public enum VariantForm {
    FULL("", "full", 1),
    SLAB("_slab", "slab", 3),
    STAIRS("_stairs", "stairs", 80),
    WALL("_wall", "wall", 324),
    FENCE("_fence", "fence", 32),
    FENCE_GATE("_fence_gate", "fence_gate", 32),
    BUTTON("_button", "button", 24),
    PRESSURE_PLATE("_pressure_plate", "pressure_plate", 2);

    private final String suffix;
    private final String id;
    private final int estimatedStateCount;

    VariantForm(String suffix, String id, int estimatedStateCount) {
        this.suffix = suffix;
        this.id = id;
        this.estimatedStateCount = estimatedStateCount;
    }

    public String suffix() {
        return this.suffix;
    }

    public String id() {
        return this.id;
    }

    public int estimatedStateCount() {
        return this.estimatedStateCount;
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
        };
    }

}