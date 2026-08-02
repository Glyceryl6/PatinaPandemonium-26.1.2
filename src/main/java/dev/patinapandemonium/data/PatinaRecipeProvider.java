package dev.patinapandemonium.data;

import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;

/**
 * Optional exporter for every generated server-data resource.
 */
public class PatinaRecipeProvider extends AbstractGeneratedProvider {

    public PatinaRecipeProvider(PackOutput output) {
        super(output, PackType.SERVER_DATA);
    }

    @Override
    protected boolean accepts(String path) {
        return path.startsWith("data/");
    }

    @Override
    public String getName() {
        return "Patina optional server-data export";
    }

}