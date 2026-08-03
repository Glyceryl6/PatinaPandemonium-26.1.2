package dev.patinapandemonium.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

public record VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form, @Nullable DyeColor dyeColor) {

    public VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form) {
        this(sourceId, stage, waxed, form, null);
    }

    public String stageKey() {
        return "patina_pandemonium.stage." + (this.waxed ? "waxed_" : "") + this.stage.id();
    }

    public String formKey() {
        return "patina_pandemonium.form." + this.form.id();
    }

    public String dyeKey() {
        return this.dyeColor == null ? "patina_pandemonium.dye.none" : "patina_pandemonium.dye." + this.dyeColor.getSerializedName();
    }

    public String dyePath() {
        return this.dyeColor == null ? "" : this.dyeColor.getSerializedName();
    }

    public int tint() {
        int oxidation = 0xFF000000 | this.stage.fallbackColor();
        return this.dyeColor == null ? oxidation : multiply(oxidation, this.dyeColor.getTextureDiffuseColor());
    }

    private static int multiply(int color, int tint) {
        int alpha = color >>> 24;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

}