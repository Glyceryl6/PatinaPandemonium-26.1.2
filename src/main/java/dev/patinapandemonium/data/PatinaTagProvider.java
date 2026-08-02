package dev.patinapandemonium.data;

import net.minecraft.data.PackOutput;

public final class PatinaTagProvider extends AbstractGeneratedProvider {

    public PatinaTagProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected boolean accepts(String path) {
        return path.contains("/tags/");
    }

    @Override
    public String getName() {
        return "Patina block and item tags";
    }

}