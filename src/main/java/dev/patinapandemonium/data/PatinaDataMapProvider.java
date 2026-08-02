package dev.patinapandemonium.data;

import net.minecraft.data.PackOutput;

public class PatinaDataMapProvider extends AbstractGeneratedProvider {

    public PatinaDataMapProvider(PackOutput o) {
        super(o);
    }

    protected boolean accepts(String p) {
        return p.contains("/data_maps/") || p.endsWith("patina_manifest.json");
    }

    public String getName() {
        return "Patina oxidation and wax data maps";
    }

}