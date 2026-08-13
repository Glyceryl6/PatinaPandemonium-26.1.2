package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form,
                          @Nullable DyeColor dyeColor, @Nullable Integer customColor) {

    private static final int NO_DYE = -1;
    public static final Codec<VariantData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("source").forGetter(VariantData::sourceId),
            Codec.intRange(0, OxidationStage.values().length - 1).fieldOf("stage").forGetter(data -> data.stage().ordinal()),
            Codec.BOOL.fieldOf("waxed").forGetter(VariantData::waxed),
            Codec.intRange(0, VariantForm.values().length - 1).fieldOf("form").forGetter(data -> data.form().ordinal()),
            Codec.intRange(NO_DYE, DyeColor.VALUES.size() - 1).fieldOf("dye").forGetter(VariantData::dyeId),
            Codec.intRange(0, 0xFFFFFF).optionalFieldOf("custom_color").forGetter(data -> Optional.ofNullable(data.customColor()))
    ).apply(instance, VariantData::decode));
    public static final StreamCodec<RegistryFriendlyByteBuf, VariantData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form, @Nullable DyeColor dyeColor) {
        this(sourceId, stage, waxed, form, dyeColor, null);
    }

    public VariantData(Identifier sourceId, OxidationStage stage, boolean waxed, VariantForm form) {
        this(sourceId, stage, waxed, form, null, null);
    }

    public static VariantData defaultFor(VariantForm form) {
        return new VariantData(BuiltInRegistries.BLOCK.getKey(Blocks.STONE), OxidationStage.FRESH, false, form, null, null);
    }

    public VariantData normalized(VariantForm form) {
        Block source = BuiltInRegistries.BLOCK.getValue(this.sourceId);
        Identifier sourceId = source == Blocks.AIR
                ? BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
                : BuiltInRegistries.BLOCK.getKey(source);
        return new VariantData(sourceId, this.stage, this.waxed, form, this.dyeColor, this.customColor);
    }

    public VariantData withSourceId(Identifier sourceId) {
        return new VariantData(sourceId, this.stage, this.waxed, this.form, this.dyeColor, this.customColor);
    }

    public VariantData withStage(OxidationStage stage) {
        return new VariantData(this.sourceId, stage, this.waxed, this.form, this.dyeColor, this.customColor);
    }

    public VariantData withWaxed(boolean waxed) {
        return new VariantData(this.sourceId, this.stage, waxed, this.form, this.dyeColor, this.customColor);
    }

    public VariantData withForm(VariantForm form) {
        return new VariantData(this.sourceId, this.stage, this.waxed, form, this.dyeColor, this.customColor);
    }

    public VariantData withDye(@Nullable DyeColor dyeColor) {
        return new VariantData(this.sourceId, this.stage, this.waxed, this.form, dyeColor, null);
    }

    public VariantData withCustomColor(@Nullable Integer customColor) {
        return new VariantData(this.sourceId, this.stage, this.waxed, this.form, this.dyeColor, customColor);
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
        int overlay = this.customColor == null
            ? this.dyeColor == null ? 0xFFFFFFFF : 0xFF000000 | this.dyeColor.getTextureDiffuseColor()
            : 0xFF000000 | this.customColor;
        return multiply(oxidation, overlay);
    }

    public int dyeId() {
        return this.dyeColor == null ? NO_DYE : this.dyeColor.getId();
    }

    public static VariantData decode(Identifier sourceId, int stage, boolean waxed, int form, int dye) {
        return new VariantData(sourceId, OxidationStage.byOrdinal(stage), waxed, VariantForm.byOrdinal(form), dyeById(dye), null);
    }

    private static VariantData decode(Identifier sourceId, int stage, boolean waxed, int form, int dye, Optional<Integer> customColor) {
        return new VariantData(sourceId, OxidationStage.byOrdinal(stage), waxed, VariantForm.byOrdinal(form), dyeById(dye), customColor.orElse(null));
    }

    public static DyeColor dyeById(int dye) {
        return dye < 0 || dye >= DyeColor.VALUES.size() ? null : DyeColor.VALUES.get(dye);
    }

    private static int multiply(int color, int tint) {
        int alpha = color >>> 24;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

}