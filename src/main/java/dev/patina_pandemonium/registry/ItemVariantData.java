package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record ItemVariantData(Identifier sourceId, OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor,
                              @Nullable Identifier modelId, @Nullable Integer customColor) {

    private static final int NO_DYE = -1;
    public static final Codec<ItemVariantData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("source").forGetter(ItemVariantData::sourceId),
        Codec.intRange(0, OxidationStage.values().length - 1).fieldOf("stage").forGetter(data -> data.stage().ordinal()),
        Codec.BOOL.fieldOf("waxed").forGetter(ItemVariantData::waxed),
        Codec.intRange(NO_DYE, DyeColor.VALUES.size() - 1).fieldOf("dye").forGetter(ItemVariantData::dyeId),
        Identifier.CODEC.optionalFieldOf("model").forGetter(data -> Optional.ofNullable(data.modelId())),
        Codec.intRange(0, 0xFFFFFF).optionalFieldOf("custom_color").forGetter(data -> Optional.ofNullable(data.customColor()))
    ).apply(instance, ItemVariantData::decode));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemVariantData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public ItemVariantData(Identifier sourceId, OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor, @Nullable Identifier modelId) {
        this(sourceId, stage, waxed, dyeColor, modelId, null);
    }

    public static ItemVariantData defaultData() {
        Identifier stone = BuiltInRegistries.ITEM.getKey(Items.STONE);
        return new ItemVariantData(stone, OxidationStage.FRESH, false, null, stone, null);
    }

    public ItemVariantData normalized(Item item) {
        Item source = BuiltInRegistries.ITEM.getValue(this.sourceId);
        Identifier sourceId = source == Items.AIR ? BuiltInRegistries.ITEM.getKey(item) : BuiltInRegistries.ITEM.getKey(source);
        Identifier modelId = DynamicVariantRegistry.VARIANT_ITEM_MODEL.equals(this.modelId) ? sourceId : this.modelId;
        return new ItemVariantData(sourceId, this.stage, this.waxed, this.dyeColor, modelId, this.customColor);
    }

    public VariantData forBlock(Identifier sourceId) {
        return new VariantData(sourceId, this.stage, this.waxed, VariantForm.FULL, this.dyeColor, this.customColor);
    }

    public ItemVariantData withStage(OxidationStage stage) {
        return new ItemVariantData(this.sourceId, stage, this.waxed, this.dyeColor, this.modelId, this.customColor);
    }

    public ItemVariantData withWaxed(boolean waxed) {
        return new ItemVariantData(this.sourceId, this.stage, waxed, this.dyeColor, this.modelId, this.customColor);
    }

    public ItemVariantData withCustomColor(@Nullable Integer customColor) {
        return new ItemVariantData(this.sourceId, this.stage, this.waxed, this.dyeColor, this.modelId, customColor);
    }

    public String stageKey() {
        return "patina_pandemonium.stage." + (this.waxed ? "waxed_" : "") + this.stage.id();
    }

    public String dyeKey() {
        return this.dyeColor == null ? "patina_pandemonium.dye.none" : "patina_pandemonium.dye." + this.dyeColor.getSerializedName();
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

    private static ItemVariantData decode(Identifier sourceId, int stage, boolean waxed, int dye,
                                          Optional<Identifier> modelId, Optional<Integer> customColor) {
        return new ItemVariantData(sourceId, OxidationStage.byOrdinal(stage), waxed, VariantData.dyeById(dye), modelId.orElse(null), customColor.orElse(null));
    }

    private static int multiply(int color, int tint) {
        int alpha = color >>> 24;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

}
