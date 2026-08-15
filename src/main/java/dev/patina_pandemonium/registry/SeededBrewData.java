package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SeededBrewData(long seed, int ingredientCount, int redstoneCount, int glowstoneCount) {

    public static final Codec<SeededBrewData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("seed").forGetter(SeededBrewData::seed),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("ingredients").forGetter(SeededBrewData::ingredientCount),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("redstone", 0).forGetter(SeededBrewData::redstoneCount),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("glowstone", 0).forGetter(SeededBrewData::glowstoneCount)
    ).apply(instance, SeededBrewData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeededBrewData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

}