package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.patina_pandemonium.config.PatinaRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runtime-only virtual chemistry for crafting results. The values are deterministic game mechanics, not claims about real-world substances. */
public class CraftingChemistry {

    private static final int NO_COLOR = -1;
    private static final int NO_HUE = 31;
    private static final int NEUTRAL_HUE = 16;
    private static final int ELEMENT_C = 0;
    private static final int ELEMENT_H = 1;
    private static final int ELEMENT_N = 2;
    private static final int ELEMENT_O = 3;
    private static final int ELEMENT_CU = 4;
    private static final int ELEMENT_SI = 5;
    private static final int ELEMENT_COUNT = 6;
    private static final long[] ATOMIC_MASS_MILLI = {12_011L, 1_008L, 14_007L, 15_999L, 63_546L, 28_085L};

    public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(NO_COLOR, 0xFFFFFF).fieldOf("color").forGetter(Data::color),
        Codec.intRange(0, 3_000).fieldOf("oxidation_permille").forGetter(Data::oxidationPermille),
        Codec.intRange(0, 1_000).fieldOf("wax_permille").forGetter(Data::waxPermille),
        Codec.STRING.listOf().fieldOf("elements").forGetter(Data::elements),
        Codec.STRING.fieldOf("molar_mass_milli").forGetter(Data::molarMassMilli),
        Codec.STRING.fieldOf("polymer_degree").forGetter(Data::polymerDegree),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("generation").forGetter(Data::generation),
        Codec.intRange(0, 4).fieldOf("topology").forGetter(Data::topology),
        Codec.LONG.fieldOf("signature").forGetter(Data::signature),
        Codec.INT.listOf().fieldOf("groups").forGetter(Data::groups)).apply(instance, Data::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static Data emptyData() {
        return new Data(NO_COLOR, 0, 0, List.of("0", "0", "0", "0", "0", "0"),
            "0", "1", 1, 0, 0L, List.of());
    }

    public static Data retarget(Data data, OxidationStage stage, boolean waxed, @Nullable DyeColor dye, @Nullable Integer customColor) {
        int color = customColor == null ? dye == null ? NO_COLOR : dye.getTextureDiffuseColor() & 0xFFFFFF : customColor & 0xFFFFFF;
        ColorClass colorClass = color == NO_COLOR ? new ColorClass(NO_HUE, 0, 0) : classify(color);
        int mutableMask = 3 << 12 | 1 << 14 | 0x1F << 15 | 3 << 20 | 3 << 22;
        ArrayList<Integer> groups = new ArrayList<>(data.groups().size());
        boolean changed = data.color() != color || data.oxidationPermille() != stage.ordinal() * 1_000
            || data.waxPermille() != (waxed ? 1_000 : 0);
        for (int group : data.groups()) {
            int retargeted = group & ~mutableMask
                | stage.ordinal() << 12
                | (waxed ? 1 : 0) << 14
                | colorClass.hue() << 15
                | colorClass.saturation() << 20
                | colorClass.value() << 22;
            groups.add(retargeted);
            changed |= retargeted != group;
        }
        if (!changed) return data;
        long signature = mix64(data.signature() ^ ((long) stage.ordinal() << 48)
            ^ (waxed ? 0x5A5A5A5A5A5A5A5AL : 0L) ^ (color == NO_COLOR ? 0L : color));
        return new Data(color, stage.ordinal() * 1_000, waxed ? 1_000 : 0, data.elements(),
            data.molarMassMilli(), data.polymerDegree(), data.generation(), data.topology(), signature, groups);
    }

    @Nullable
    public static Synthesis synthesize(CraftingInput input) {
        if (input.isEmpty()) return null;
        int width = Math.max(1, input.width());
        int height = Math.max(1, input.height());
        BigInteger[] elements = zeroElements();
        BigInteger polymerDegree = BigInteger.ONE;
        double oxidationTotal = 0.0D;
        double oxidationWeight = 0.0D;
        double waxWeight = 0.0D;
        double[] absorbance = new double[3];
        double colorWeight = 0.0D;
        DyeColor sharedDye = null;
        boolean dyeInitialized = false;
        boolean sharedDyeValid = true;
        boolean hasVariant = false;
        boolean hasPriorChemistry = false;
        int generation = 1;
        long signature = mix64((long) width << 32 ^ height);
        ArrayList<Integer> groups = new ArrayList<>();
        boolean[] variantSlots = new boolean[input.size()];
        int occupied = 0;
        for (ItemStack ingredient : input.items()) if (!ingredient.isEmpty()) occupied++;
        int groupBudget = Math.max(1, PatinaRules.INSTANCE.maximumChemicalNameGroups / Math.max(1, occupied));

        for (int index = 0; index < input.size(); index++) {
            ItemStack ingredient = input.getItem(index);
            if (ingredient.isEmpty()) continue;
            int row = index / width;
            int column = index % width;
            int locant = index + 1;
            long slotFactor = 1L + locant + (long) (row + 1) * (column + 2);
            ItemVariantData variant = DynamicVariantRegistry.variantUseData(ingredient);
            Data prior = ingredient.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
            Identifier sourceId = sourceId(ingredient, variant);
            long sourceHash = mix64(sourceId.toString().hashCode() * 0x9E3779B97F4A7C15L);
            BigInteger factor = BigInteger.valueOf(slotFactor);

            if (prior == null) addPseudoElements(elements, sourceHash, variant, factor);
            else {
                addElements(elements, prior.elementValues(), factor);
                int priorGeneration = prior.generation() == Integer.MAX_VALUE ? Integer.MAX_VALUE : prior.generation() + 1;
                generation = Math.max(generation, priorGeneration);
                polymerDegree = bounded(polymerDegree.multiply(prior.polymerDegreeValue().max(BigInteger.ONE)));
                signature = mix64(signature ^ prior.signature());
                hasPriorChemistry = true;
                int inheritedGroups = 0;
                int inheritedGroupLimit = Math.max(0, groupBudget - (variant == null ? 0 : 1));
                for (int priorGroup : prior.groups()) {
                    if (groups.size() >= PatinaRules.INSTANCE.maximumChemicalNameGroups || inheritedGroups >= inheritedGroupLimit) break;
                    int priorLocant = Math.max(1, priorGroup & 0xFFF);
                    int nestedLocant = 1 + (int) Math.floorMod((priorLocant - 1L) * 37L + (locant - 1L) * 131L
                        + (long) prior.generation() * 17L, 0xFFFL);
                    groups.add(priorGroup & ~0xFFF | nestedLocant);
                    inheritedGroups++;
                }
            }

            polymerDegree = bounded(polymerDegree.multiply(BigInteger.valueOf(polymerFactor(locant, sourceHash, variant, prior))));
            signature = mix64(signature ^ sourceHash ^ Long.rotateLeft(slotFactor, index & 63));
            if (variant == null) continue;
            hasVariant = true;
            variantSlots[index] = true;
            double equivalent = slotFactor * (1.0D + variant.stage().ordinal() * 0.35D) * (variant.waxed() ? 0.82D : 1.0D);
            oxidationTotal += variant.stage().ordinal() * equivalent;
            oxidationWeight += equivalent;
            if (variant.waxed()) waxWeight += equivalent;

            Integer postColor = postColor(variant, prior);
            if (postColor != null) {
                double chromaWeight = equivalent * (variant.waxed() ? 0.72D : 1.0D);
                addAbsorbance(absorbance, postColor, chromaWeight);
                colorWeight += chromaWeight;
            }

            if (variant.customColor() != null || variant.dyeColor() == null) sharedDyeValid = false;
            else if (!dyeInitialized) {
                sharedDye = variant.dyeColor();
                dyeInitialized = true;
            } else if (sharedDye != variant.dyeColor()) sharedDyeValid = false;
            if (groups.size() < PatinaRules.INSTANCE.maximumChemicalNameGroups) {
                groups.add(packGroup(locant, variant, postColor, sourceHash, prior != null));
            }

            signature = mix64(signature ^ ((long) variant.stage().ordinal() << 48)
                ^ (variant.waxed() ? 0x5A5A5A5A5A5A5A5AL : 0L) ^ (postColor == null ? 0L : postColor));
        }

        if (!hasVariant || oxidationWeight <= 0.0D) return null;
        int oxidationPermille = Math.clamp((int) Math.round(oxidationTotal / oxidationWeight * 1_000.0D), 0, 3_000);
        int waxPermille = Math.clamp((int) Math.round(waxWeight / oxidationWeight * 1_000.0D), 0, 1_000);
        OxidationStage stage = OxidationStage.byOrdinal(Math.clamp((int) Math.round(oxidationPermille / 1_000.0D), 0, 3));
        boolean waxed = waxPermille >= 500;
        Integer customColor = colorWeight <= 0.0D ? null : mixedColor(absorbance, colorWeight);
        DyeColor dye = sharedDyeValid && dyeInitialized ? sharedDye : null;
        int topology = topology(variantSlots, width, height, hasPriorChemistry);
        polymerDegree = bounded(polymerDegree.multiply(factorial(Math.min(occupied, 64))));
        BigInteger molarMass = molarMass(elements);
        Data data = new Data(customColor == null ? NO_COLOR : customColor, oxidationPermille, waxPermille,
            encodeElements(elements), molarMass.toString(), polymerDegree.toString(), generation, topology,
            mix64(signature ^ molarMass.hashCode() ^ polymerDegree.hashCode()), List.copyOf(groups));
        return new Synthesis(stage, waxed, dye, customColor, data);
    }

    public static Component name(ItemStack stack, Data data) {
        Component sourceName = sourceName(stack);
        Component color = data.color() == NO_COLOR
            ? Component.translatable("item.patina_pandemonium.chemistry.color.none") : colorName(data.color());
        MutableComponent groupList = Component.empty();
        for (int index = 0; index < data.groups().size(); index++) {
            if (index > 0) groupList.append(Component.translatable("item.patina_pandemonium.chemistry.separator"));
            groupList.append(groupName(data.groups().get(index)));
        }
        if (data.groups().isEmpty()) {
            int aggregateStage = Math.clamp((int) Math.round(data.oxidationPermille() / 1_000.0D), 0, 3);
            groupList.append(Component.translatable("item.patina_pandemonium.chemistry.group.aggregate",
                Component.translatable("item.patina_pandemonium.chemistry.oxidation." + aggregateStage),
                Component.translatable("item.patina_pandemonium.chemistry.wax." + (data.waxPermille() >= 500 ? "waxed" : "bare")), color));
        }
        boolean polymer = data.generation() > 1 || data.groups().size() > 1 || data.polymerDegreeValue().compareTo(BigInteger.ONE) > 0;
        Component stereo = Component.translatable("item.patina_pandemonium.chemistry.stereo.prefix",
            Component.translatable("item.patina_pandemonium.chemistry.stereo." + (data.signature() & 3L)));
        Component topology = Component.translatable("item.patina_pandemonium.chemistry.topology." + data.topology());
        Component polymerMode = Component.translatable("item.patina_pandemonium.chemistry.polymer_mode."
            + (polymer ? Long.toString(data.signature() >>> 2 & 7L) : "mono"));
        Component generation = Component.translatable("item.patina_pandemonium.chemistry.generation", data.generation());
        return Component.translatable(polymer
                ? "item.patina_pandemonium.chemistry.name.polymer" : "item.patina_pandemonium.chemistry.name.monomer",
            stereo, groupList, polymerMode, topology, sourceName, color, generation);
    }

    public static Component sourceName(ItemStack stack) {
        Item sourceItem = stack.getItem();
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null) {
            Item source = BuiltInRegistries.ITEM.getValue(itemData.sourceId());
            if (source != Items.AIR) sourceItem = source;
        } else {
            VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
            if (blockData != null) {
                Block sourceBlock = DynamicVariantRegistry.existingForm(blockData.sourceId(), blockData.form());
                if (sourceBlock == null || sourceBlock == Blocks.AIR) sourceBlock = BuiltInRegistries.BLOCK.getValue(blockData.sourceId());
                if (sourceBlock != Blocks.AIR && sourceBlock.asItem() != Items.AIR) {
                    sourceItem = sourceBlock.asItem();
                    if (blockData.form() != VariantForm.FULL && DynamicVariantRegistry.existingForm(blockData.sourceId(), blockData.form()) == null) {
                        Component baseName = sourceItem.getName(sourceItem.getDefaultInstance());
                        return baseName.copy().append(Component.translatable(blockData.formKey()));
                    }
                }
            }
        }

        ItemStack sourceStack = sourceItem.getDefaultInstance();
        Component configured = sourceStack.get(DataComponents.ITEM_NAME);
        return configured == null ? Component.translatable(sourceItem.getDescriptionId()) : configured;
    }

    private static Component groupName(int packed) {
        int locant = packed & 0xFFF;
        int stage = packed >>> 12 & 0x3;
        boolean waxed = (packed >>> 14 & 0x1) != 0;
        int hue = packed >>> 15 & 0x1F;
        int saturation = packed >>> 20 & 0x3;
        int value = packed >>> 22 & 0x3;
        boolean branch = (packed >>> 24 & 0x1) != 0;
        int chain = packed >>> 25 & 0xF;
        Component color = hue == NO_HUE
            ? Component.translatable("item.patina_pandemonium.chemistry.chromato.none")
            : Component.translatable("item.patina_pandemonium.chemistry.chromato.colored",
                Component.translatable("item.patina_pandemonium.chemistry.value." + value),
                Component.translatable("item.patina_pandemonium.chemistry.saturation." + saturation),
                Component.translatable("item.patina_pandemonium.chemistry.hue." + hue));
        return Component.translatable("item.patina_pandemonium.chemistry.group",
            locant,
            Component.translatable("item.patina_pandemonium.chemistry.chain." + chain),
            Component.translatable("item.patina_pandemonium.chemistry.oxidation." + stage),
            Component.translatable("item.patina_pandemonium.chemistry.wax." + (waxed ? "waxed" : "bare")),
            color,
            Component.translatable("item.patina_pandemonium.chemistry.branch." + (branch ? "poly" : "mono")));
    }

    private static Component colorName(int color) {
        ColorClass colorClass = classify(color);
        return Component.translatable("item.patina_pandemonium.chemistry.color",
            Component.translatable("item.patina_pandemonium.chemistry.value." + colorClass.value()),
            Component.translatable("item.patina_pandemonium.chemistry.saturation." + colorClass.saturation()),
            Component.translatable("item.patina_pandemonium.chemistry.hue." + colorClass.hue()));
    }

    private static int packGroup(int locant, ItemVariantData variant, @Nullable Integer postColor, long sourceHash, boolean branch) {
        ColorClass colorClass = postColor == null ? new ColorClass(NO_HUE, 0, 0) : classify(postColor);
        int chain = Math.floorMod((int) sourceHash, 10);
        return Math.min(locant, 0xFFF)
            | variant.stage().ordinal() << 12
            | (variant.waxed() ? 1 : 0) << 14
            | colorClass.hue() << 15
            | colorClass.saturation() << 20
            | colorClass.value() << 22
            | (branch ? 1 : 0) << 24
            | chain << 25;
    }

    private static ColorClass classify(int color) {
        double red = ((color >>> 16) & 0xFF) / 255.0D;
        double green = ((color >>> 8) & 0xFF) / 255.0D;
        double blue = (color & 0xFF) / 255.0D;
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double delta = max - min;
        double saturation = max <= 0.0D ? 0.0D : delta / max;
        int hue;
        if (saturation < 0.08D) hue = NEUTRAL_HUE;
        else {
            double degrees;
            if (max == red) degrees = 60.0D * (((green - blue) / delta) % 6.0D);
            else if (max == green) degrees = 60.0D * ((blue - red) / delta + 2.0D);
            else degrees = 60.0D * ((red - green) / delta + 4.0D);
            if (degrees < 0.0D) degrees += 360.0D;
            hue = Math.floorMod((int) Math.floor((degrees + 11.25D) / 22.5D), 16);
        }
        int saturationBand = saturation < 0.16D ? 0 : saturation < 0.42D ? 1 : saturation < 0.72D ? 2 : 3;
        int valueBand = max < 0.24D ? 0 : max < 0.50D ? 1 : max < 0.78D ? 2 : 3;
        return new ColorClass(hue, saturationBand, valueBand);
    }

    @Nullable
    private static Integer postColor(ItemVariantData variant, @Nullable Data prior) {
        if (variant.customColor() != null) return variant.customColor();
        if (variant.dyeColor() != null) return variant.dyeColor().getTextureDiffuseColor() & 0xFFFFFF;
        return prior != null && prior.color() != NO_COLOR ? prior.color() : null;
    }

    private static void addAbsorbance(double[] absorbance, int color, double weight) {
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            double srgb = ((color >>> shift) & 0xFF) / 255.0D;
            double linear = srgb <= 0.04045D ? srgb / 12.92D : Math.pow((srgb + 0.055D) / 1.055D, 2.4D);
            absorbance[channel] += -Math.log10(Math.max(1.0D / 65_535.0D, linear)) * weight;
        }
    }

    private static int mixedColor(double[] absorbance, double weight) {
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            double linear = Math.pow(10.0D, -absorbance[channel] / weight);
            double srgb = linear <= 0.0031308D ? 12.92D * linear : 1.055D * Math.pow(linear, 1.0D / 2.4D) - 0.055D;
            int value = Math.clamp((int) Math.round(srgb * 255.0D), 0, 255);
            result |= value << (16 - channel * 8);
        }
        return result;
    }

    private static Identifier sourceId(ItemStack ingredient, @Nullable ItemVariantData variant) {
        if (variant != null) return variant.sourceId();
        return BuiltInRegistries.ITEM.getKey(ingredient.getItem());
    }

    private static void addPseudoElements(BigInteger[] result, long hash, @Nullable ItemVariantData variant, BigInteger factor) {
        long positive = hash & Long.MAX_VALUE;
        long[] counts = {
            1L + positive % 12L,
            2L + (positive >>> 5) % 24L,
            (positive >>> 11) % 5L,
            (positive >>> 17) % 6L,
            (positive >>> 23) % 3L,
            (positive >>> 29) % 4L};
        if (variant != null) {
            counts[ELEMENT_O] += variant.stage().ordinal() * 2L;
            counts[ELEMENT_CU] += variant.stage().ordinal();
            if (variant.waxed()) {
                counts[ELEMENT_C] += 2L;
                counts[ELEMENT_H] += 4L;
                counts[ELEMENT_O] += 1L;
            }
            Integer color = postColor(variant, null);
            if (color != null) {
                counts[ELEMENT_C] += ((color >>> 16) & 0xFF) / 32L;
                counts[ELEMENT_N] += ((color >>> 8) & 0xFF) / 32L;
                counts[ELEMENT_SI] += (color & 0xFF) / 32L;
            }
        }
        for (int element = 0; element < ELEMENT_COUNT; element++) {
            result[element] = bounded(result[element].add(BigInteger.valueOf(counts[element]).multiply(factor)));
        }
    }

    private static void addElements(BigInteger[] result, BigInteger[] source, BigInteger factor) {
        for (int element = 0; element < ELEMENT_COUNT; element++) {
            result[element] = bounded(result[element].add(source[element].multiply(factor)));
        }
    }

    private static BigInteger molarMass(BigInteger[] elements) {
        BigInteger result = BigInteger.ZERO;
        for (int element = 0; element < ELEMENT_COUNT; element++) {
            result = bounded(result.add(elements[element].multiply(BigInteger.valueOf(ATOMIC_MASS_MILLI[element]))));
        }
        return result;
    }

    private static long polymerFactor(int locant, long sourceHash, @Nullable ItemVariantData variant, @Nullable Data prior) {
        long factor = 2L + locant + Math.floorMod(sourceHash, 11L);
        if (variant != null) factor += variant.stage().ordinal() * 3L + (variant.waxed() ? 5L : 0L) + (postColor(variant, prior) == null ? 0L : 7L);
        if (prior != null) factor += Math.min(97, prior.generation() * 2L + prior.groups().size());
        return Math.max(2L, factor);
    }

    private static int topology(boolean[] slots, int width, int height, boolean priorChemistry) {
        int vertices = 0;
        int edges = 0;
        int branchVertices = 0;
        for (int index = 0; index < slots.length; index++) {
            if (!slots[index]) continue;
            vertices++;
            int row = index / width;
            int column = index % width;
            int degree = 0;
            if (column > 0 && slots[index - 1]) degree++;
            if (column + 1 < width && index + 1 < slots.length && slots[index + 1]) degree++;
            if (row > 0 && slots[index - width]) degree++;
            if (row + 1 < height && index + width < slots.length && slots[index + width]) degree++;
            edges += degree;
            if (degree >= 3) branchVertices++;
        }
        edges /= 2;
        if (vertices <= 1 && !priorChemistry) return 0;
        if (vertices >= 4 && edges >= vertices && branchVertices == 0) return 4;
        if (branchVertices >= 2 || priorChemistry && branchVertices > 0) return 3;
        if (branchVertices == 1 || priorChemistry) return 2;
        return 1;
    }

    private static BigInteger factorial(int value) {
        BigInteger result = BigInteger.ONE;
        for (int index = 2; index <= value; index++) result = bounded(result.multiply(BigInteger.valueOf(index)));
        return result;
    }

    private static BigInteger bounded(BigInteger value) {
        int maximumBits = Math.max(128, PatinaRules.INSTANCE.chemistryMaximumNumberBits);
        if (value.bitLength() <= maximumBits) return value;
        return BigInteger.ONE.shiftLeft(maximumBits).subtract(BigInteger.ONE);
    }

    private static BigInteger[] zeroElements() {
        BigInteger[] result = new BigInteger[ELEMENT_COUNT];
        Arrays.fill(result, BigInteger.ZERO);
        return result;
    }

    private static List<String> encodeElements(BigInteger[] elements) {
        ArrayList<String> result = new ArrayList<>(ELEMENT_COUNT);
        for (BigInteger element : elements) result.add(element.toString());
        return List.copyOf(result);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Synthesis(OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor,
                            @Nullable Integer customColor, Data data) {}

    public record Data(int color, int oxidationPermille, int waxPermille, List<String> elements,
                       String molarMassMilli, String polymerDegree, int generation, int topology,
                       long signature, List<Integer> groups) {

        public Data {
            elements = List.copyOf(elements);
            groups = List.copyOf(groups);
        }

        public BigInteger[] elementValues() {
            BigInteger[] result = zeroElements();
            for (int index = 0; index < Math.min(ELEMENT_COUNT, this.elements.size()); index++) {
                try {
                    result[index] = bounded(new BigInteger(this.elements.get(index)));
                } catch (NumberFormatException ignored) {
                    result[index] = BigInteger.ZERO;
                }
            }
            return result;
        }

        public BigInteger molarMassValue() {
            try {
                return bounded(new BigInteger(this.molarMassMilli));
            } catch (NumberFormatException ignored) {
                return BigInteger.ZERO;
            }
        }

        public BigInteger polymerDegreeValue() {
            try {
                return bounded(new BigInteger(this.polymerDegree));
            } catch (NumberFormatException ignored) {
                return BigInteger.ONE;
            }
        }
    }

    private record ColorClass(int hue, int saturation, int value) {}

}