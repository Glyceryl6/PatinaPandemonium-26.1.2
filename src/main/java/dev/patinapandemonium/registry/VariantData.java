package dev.patinapandemonium.registry;

import net.minecraft.resources.Identifier;

public record VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form) {

    public String stageKey() {
        return "patina_pandemonium.stage." + (waxed ? "waxed_" : "") + stage.id();
    }

    public String formKey() {
        return "patina_pandemonium.form." + form.id();
    }

}