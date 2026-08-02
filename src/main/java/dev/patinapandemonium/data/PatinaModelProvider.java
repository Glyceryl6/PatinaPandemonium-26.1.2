package dev.patinapandemonium.data;

import net.minecraft.data.PackOutput;

public class PatinaModelProvider extends AbstractGeneratedProvider {

    public PatinaModelProvider(PackOutput o) {
        super(o);
    }

    protected boolean accepts(String p) {
        return p.startsWith("assets/");
    }

    public String getName() {
        return "Patina models, blockstates, client items and generated tints";
    }

}