package dev.patinapandemonium.registry;

import dev.patinapandemonium.config.PatinaRules;

import java.util.function.Predicate;

public enum VariantForm {

    FULL("", "full", 1, rules -> true),
    SLAB("_slab", "slab", 6, rules -> rules.slabs),
    STAIRS("_stairs", "stairs", 80, rules -> rules.stairs),
    WALL("_wall", "wall", 324, rules -> rules.walls),
    FENCE("_fence", "fence", 32, rules -> rules.fences),
    FENCE_GATE("_fence_gate", "fence_gate", 32, rules -> rules.fenceGates),
    CARPET("_carpet", "carpet", 1, rules -> rules.carpets),
    BUTTON("_button", "button", 24, rules -> rules.buttons),
    PRESSURE_PLATE("_pressure_plate", "pressure_plate", 2, rules -> rules.pressurePlates);

    private final String suffix;
    private final String id;
    private final int estimatedStateCount;
    private final Predicate<PatinaRules> enabled;

    VariantForm(String suffix, String id, int estimatedStateCount, Predicate<PatinaRules> enabled) {
        this.suffix = suffix;
        this.id = id;
        this.estimatedStateCount = estimatedStateCount;
        this.enabled = enabled;
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
        return this.enabled.test(rules);
    }

}