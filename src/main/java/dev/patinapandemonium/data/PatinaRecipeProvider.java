package dev.patinapandemonium.data;

import net.minecraft.data.PackOutput;

public final class PatinaRecipeProvider extends AbstractGeneratedProvider {

    public PatinaRecipeProvider(PackOutput o) {
        super(o);
    }

    protected boolean accepts(String p) {
        return p.contains("/recipe/") || p.contains("/loot_table/");
    }

    public String getName() {
        return "Patina recipes and loot tables";
    }

}