package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Persistent phenotype and compact display cache for one seeded brew. The complete manufacturing identity is stored
 * separately in {@link VariantProvenance}; this component is intentionally small enough to stay on every bottle.
 */
public record SeededBrewData(long seed, int ingredientCount, int generation, int potencyBudget, int redstoneCount,
                             int glowstoneCount, int delivery, int lineageLength, int parentCount,
                             List<Affinity> affinities, List<String> nameSegments) {

    public static final int DELIVERY_DRINKABLE = 0;
    public static final int DELIVERY_SPLASH = 1;
    public static final int DELIVERY_LINGERING = 2;

    public static final Codec<Affinity> AFFINITY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("effect").forGetter(Affinity::effectId),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("weight").forGetter(Affinity::weight)
    ).apply(instance, Affinity::new));

    public static final Codec<SeededBrewData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("seed").forGetter(SeededBrewData::seed),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("ingredients").forGetter(SeededBrewData::ingredientCount),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("generation", 1).forGetter(SeededBrewData::generation),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("potency_budget", 0).forGetter(SeededBrewData::potencyBudget),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("redstone", 0).forGetter(SeededBrewData::redstoneCount),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("glowstone", 0).forGetter(SeededBrewData::glowstoneCount),
        Codec.intRange(DELIVERY_DRINKABLE, DELIVERY_LINGERING).optionalFieldOf("delivery", DELIVERY_DRINKABLE).forGetter(SeededBrewData::delivery),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("lineage_length", 0).forGetter(SeededBrewData::lineageLength),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("parents", 0).forGetter(SeededBrewData::parentCount),
        AFFINITY_CODEC.listOf().optionalFieldOf("affinities", List.of()).forGetter(SeededBrewData::affinities),
        Codec.STRING.listOf().optionalFieldOf("name_segments", List.of()).forGetter(SeededBrewData::nameSegments)
    ).apply(instance, SeededBrewData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeededBrewData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public SeededBrewData {
        ingredientCount = Math.max(0, ingredientCount);
        generation = Math.max(1, generation);
        potencyBudget = Math.max(0, potencyBudget);
        redstoneCount = Math.max(0, redstoneCount);
        glowstoneCount = Math.max(0, glowstoneCount);
        delivery = Math.clamp(delivery, DELIVERY_DRINKABLE, DELIVERY_LINGERING);
        lineageLength = Math.max(0, lineageLength);
        parentCount = Math.max(0, parentCount);
        affinities = List.copyOf(affinities);
        nameSegments = List.copyOf(nameSegments);
    }

    public SeededBrewData(long seed, int ingredientCount) {
        this(seed, ingredientCount, 1, 0, 0, 0, DELIVERY_DRINKABLE, ingredientCount, 0, List.of(), List.of());
    }

    public static SeededBrewData empty() {
        return new SeededBrewData(0L, 0, 1, 0, 0, 0, DELIVERY_DRINKABLE, 0, 0, List.of(), List.of());
    }

    public Component deliveryName() {
        return Component.translatable(switch (this.delivery) {
            case DELIVERY_SPLASH -> "tooltip.patina_pandemonium.seeded_brew.delivery.splash";
            case DELIVERY_LINGERING -> "tooltip.patina_pandemonium.seeded_brew.delivery.lingering";
            default -> "tooltip.patina_pandemonium.seeded_brew.delivery.drinkable";
        });
    }

    public ItemStack outputStack() {
        return switch (this.delivery) {
            case DELIVERY_SPLASH -> new ItemStack(Items.SPLASH_POTION);
            case DELIVERY_LINGERING -> new ItemStack(Items.LINGERING_POTION);
            default -> new ItemStack(Items.POTION);
        };
    }

    public Component displayName(ItemStack stack) {
        Component base = Component.translatable(switch (this.delivery) {
            case DELIVERY_SPLASH -> "item.patina_pandemonium.seeded_splash_potion";
            case DELIVERY_LINGERING -> "item.patina_pandemonium.seeded_lingering_potion";
            default -> "item.patina_pandemonium.seeded_potion";
        });
        ItemVariantData variant = DynamicVariantRegistry.peekItemData(stack);
        if (variant != null) base = DynamicVariantRegistry.variantName(variant, base);
        if (this.nameSegments.isEmpty()) return Component.translatable("item.patina_pandemonium.seeded_potion.generation", this.generation, base);
        Component prefix = Component.literal(String.join("-", this.nameSegments));
        int omitted = Math.max(0, this.lineageLength - this.nameSegments.size());
        if (omitted > 0) prefix = Component.translatable("item.patina_pandemonium.seeded_potion.truncated_prefix", prefix, omitted);
        return Component.translatable("item.patina_pandemonium.seeded_potion.systematic", prefix, this.generation, base);
    }

    public record Affinity(Identifier effectId, int weight) {
        public Affinity {
            weight = Math.max(0, weight);
        }
    }

}