package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.patina_pandemonium.config.PatinaRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compact diploid genome for breedable variants. Four linkage groups are used instead of independent random traits,
 * so offspring receive one recombined gamete from each parent. The current oxidized/color phenotype is recorded only
 * as a weak parental imprint; it does not rewrite the inherited chromosome pair of an existing animal.
 */
public class VariantGenetics {

    public static final int SCHEMA_VERSION = 1;
    public static final int LOCUS_COUNT = 12;
    private static final int[] CHROMOSOME_STARTS = {0, 3, 6, 9, 12};
    private static final int[] MAX_ALLELES = {3, 10_000, 1, 255, 255, 255, 255, 255, 255, 10_000, 10_000, 10_000};
    private static final String[] LOCUS_NAMES = {"Ox", "Or", "Wx", "R1", "G1", "B1", "R2", "G2", "B2", "Rec", "Mut", "Lin"};
    private static final List<Integer> DEFAULT_HAPLOTYPE = List.of(0, 5_000, 0, 255, 255, 255, 255, 255, 255, 5_000, 5_000, 5_000);

    public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("schema_version").forGetter(Data::schemaVersion),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("generation").forGetter(Data::generation),
        Codec.LONG.fieldOf("lineage_signature").forGetter(Data::lineageSignature),
        Codec.LONG.fieldOf("parent_alpha").forGetter(Data::parentAlpha),
        Codec.LONG.fieldOf("parent_beta").forGetter(Data::parentBeta),
        Codec.INT.listOf().fieldOf("homolog_alpha").forGetter(Data::homologAlpha),
        Codec.INT.listOf().fieldOf("homolog_beta").forGetter(Data::homologBeta),
        Codec.LONG.listOf().fieldOf("ancestors").forGetter(Data::ancestors),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("recombinations").forGetter(Data::recombinations),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("mutations").forGetter(Data::mutations),
        Codec.intRange(0, 1_000).fieldOf("heterozygosity_permille").forGetter(Data::heterozygosityPermille),
        Codec.intRange(0, 1_000).fieldOf("inbreeding_permille").forGetter(Data::inbreedingPermille),
        Codec.intRange(0, 3_000).fieldOf("imprint_oxidation_permille").forGetter(Data::imprintOxidationPermille),
        Codec.intRange(0, 0xFFFFFF).fieldOf("imprint_color").forGetter(Data::imprintColor),
        Codec.intRange(0, 1_000).fieldOf("imprint_wax_permille").forGetter(Data::imprintWaxPermille),
        Codec.intRange(0, 1_000).fieldOf("imprint_strength_permille").forGetter(Data::imprintStrengthPermille)
    ).apply(instance, Data::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static Data defaultData() {
        return normalized(new Data(SCHEMA_VERSION, 1, 1L, 0L, 0L, DEFAULT_HAPLOTYPE, DEFAULT_HAPLOTYPE, List.of(), 0, 0, 0, 0, 0, 0xFFFFFF, 0, 0));
    }

    @Nullable
    public static Data get(ItemStack stack) {
        return stack.get(DynamicVariantRegistry.GENETICS.get());
    }

    public static Data initialize(Entity entity) {
        Data existing = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
        if (existing != null) return existing;
        ItemVariantData variant = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
        VariantProvenance.Data provenance = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get());
        int stage = variant == null ? 0 : variant.stage().ordinal();
        int color = variantColor(variant);
        int wax = variant != null && variant.waxed() ? 1 : 0;
        long seed = mix64(entity.getUUID().getMostSignificantBits() ^ Long.rotateLeft(entity.getUUID().getLeastSignificantBits(), 23)
            ^ BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().hashCode());
        if (chemistry != null) seed = mix64(seed ^ chemistry.signature());
        if (provenance != null) seed = mix64(seed ^ provenance.rootFingerprint());
        ArrayList<Integer> alpha = initialHaplotype(seed, stage, wax, color, false);
        ArrayList<Integer> beta = initialHaplotype(mix64(seed ^ 0x72D36A5B4C19E307L), stage, wax, color, true);
        long lineage = genomeFingerprint(alpha, beta, seed, 1);
        Data created = normalized(new Data(SCHEMA_VERSION, 1, lineage, 0L, 0L, alpha, beta, List.of(), 0, 0,
            heterozygosity(alpha, beta), 0, stage * 1_000, color, wax * 1_000, 0));
        entity.setData(DynamicVariantRegistry.ENTITY_GENETICS.get(), created);
        return created;
    }

    public static Data breed(Entity parentAlphaEntity, Entity parentBetaEntity, Entity child, RandomSource random) {
        Data alpha = initialize(parentAlphaEntity);
        Data beta = initialize(parentBetaEntity);
        ItemVariantData alphaVariant = parentAlphaEntity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemVariantData betaVariant = parentBetaEntity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        Gamete alphaGamete = gamete(alpha, random);
        Gamete betaGamete = gamete(beta, random);
        List<Integer> homologAlpha = alphaGamete.alleles();
        List<Integer> homologBeta = betaGamete.alleles();
        int generation = Math.max(alpha.generation(), beta.generation()) + 1;
        List<Long> ancestors = mergeAncestors(alpha, beta);
        int inbreeding = inbreeding(alpha, beta);
        int imprintStage = Math.clamp((stage(alphaVariant) + stage(betaVariant)) * 500L, 0, 3_000);
        int imprintColor = averageColor(variantColor(alphaVariant), variantColor(betaVariant));
        int imprintWax = ((alphaVariant != null && alphaVariant.waxed() ? 1 : 0) + (betaVariant != null && betaVariant.waxed() ? 1 : 0)) * 500;
        int imprintStrength = Math.clamp((int) Math.round(PatinaRules.INSTANCE.geneticPhenotypeImprintWeight * 1_000.0D), 0, 1_000);
        long lineage = genomeFingerprint(homologAlpha, homologBeta,
            mix64(alpha.lineageSignature() ^ Long.rotateLeft(beta.lineageSignature(), 17) ^ child.getUUID().getLeastSignificantBits()), generation);
        return normalized(new Data(SCHEMA_VERSION, generation, lineage, alpha.lineageSignature(), beta.lineageSignature(),
            homologAlpha, homologBeta, ancestors, alphaGamete.recombinations() + betaGamete.recombinations(),
            alphaGamete.mutations() + betaGamete.mutations(), heterozygosity(homologAlpha, homologBeta), inbreeding,
            imprintStage, imprintColor, imprintWax, imprintStrength));
    }

    public static ItemVariantData phenotype(Data data, @Nullable ItemVariantData parentAlpha, @Nullable ItemVariantData parentBeta,
                                            Identifier fallbackSource, @Nullable Identifier fallbackModel) {
        int strength = data.imprintStrengthPermille();
        int genotypeStagePermille = (allele(data, 0, false) + allele(data, 0, true)) * 500;
        int stagePermille = weighted(genotypeStagePermille, data.imprintOxidationPermille(), strength);
        OxidationStage stage = OxidationStage.byOrdinal(Math.clamp((int) Math.round(stagePermille / 1_000.0D), 0, 3));
        int genotypeColor = genotypeColor(data);
        int customColor = blendColor(genotypeColor, data.imprintColor(), strength);
        boolean genotypeWaxed = allele(data, 2, false) != 0 || allele(data, 2, true) != 0;
        boolean waxed = genotypeWaxed || data.imprintWaxPermille() * strength >= 500_000;
        return new ItemVariantData(fallbackSource, stage, waxed, null, fallbackModel, customColor);
    }

    public static double oxidationRateMultiplier(Data data) {
        int rate = (allele(data, 1, false) + allele(data, 1, true)) / 2;
        return Math.clamp(0.50D + rate / 10_000.0D, 0.50D, 1.50D);
    }

    public static Component systematicName(ItemStack stack, Data data) {
        return Component.translatable("item.patina_pandemonium.genetics.name",
            data.generation(), shortSignature(data.parentAlpha()), shortSignature(data.parentBeta()), chromosomeNotation(data, 0),
            chromosomeNotation(data, 1), chromosomeNotation(data, 2), chromosomeNotation(data, 3),
            data.heterozygosityPermille(), data.inbreedingPermille(), CraftingChemistry.sourceName(stack));
    }

    public static Component compactPedigree(Data data) {
        return Component.translatable("item.patina_pandemonium.genetics.pedigree", data.generation(),
            shortSignature(data.parentAlpha()), shortSignature(data.parentBeta()), shortSignature(data.lineageSignature()));
    }

    public static String shortSignature(long signature) {
        return signature == 0L ? "0" : String.format(Locale.ROOT, "%08x", signature);
    }

    private static Gamete gamete(Data parent, RandomSource random) {
        ArrayList<Integer> alleles = new ArrayList<>(LOCUS_COUNT);
        int recombinations = 0;
        int mutations = 0;
        double crossoverScale = Math.clamp(0.50D + ((allele(parent, 9, false) + allele(parent, 9, true)) / 20_000.0D), 0.50D, 1.50D);
        double mutationScale = Math.clamp(0.50D + ((allele(parent, 10, false) + allele(parent, 10, true)) / 20_000.0D), 0.50D, 1.50D);
        for (int chromosome = 0; chromosome < CHROMOSOME_STARTS.length - 1; chromosome++) {
            boolean betaHomolog = random.nextBoolean();
            int start = CHROMOSOME_STARTS[chromosome];
            int end = CHROMOSOME_STARTS[chromosome + 1];
            for (int locus = start; locus < end; locus++) {
                int value = allele(parent, locus, betaHomolog);
                if (random.nextDouble() < PatinaRules.INSTANCE.geneticMutationChance * mutationScale) {
                    value = mutate(locus, value, random);
                    mutations++;
                }
                alleles.add(value);
                if (locus + 1 < end) {
                    double distance = (locus - start & 1) == 0 ? 0.75D : 1.25D;
                    if (random.nextDouble() < PatinaRules.INSTANCE.geneticCrossoverChance * crossoverScale * distance) {
                        betaHomolog = !betaHomolog;
                        recombinations++;
                    }
                }
            }
        }
        return new Gamete(List.copyOf(alleles), recombinations, mutations);
    }

    private static int mutate(int locus, int value, RandomSource random) {
        if (locus == 2) return value == 0 ? 1 : 0;
        int range = switch (locus) {
            case 0 -> 1;
            case 3, 4, 5, 6, 7, 8 -> 24;
            default -> 750;
        };
        int delta = random.nextInt(range * 2 + 1) - range;
        if (delta == 0) delta = random.nextBoolean() ? 1 : -1;
        return Math.clamp(value + delta, 0, MAX_ALLELES[locus]);
    }

    private static ArrayList<Integer> initialHaplotype(long seed, int stage, int wax, int color, boolean secondary) {
        ArrayList<Integer> result = new ArrayList<>(LOCUS_COUNT);
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;
        int[] bases = {stage, 5_000, wax, red, green, blue, red, green, blue, 5_000, 5_000, 5_000};
        long state = secondary ? mix64(seed ^ 0x9E3779B97F4A7C15L) : seed;
        for (int locus = 0; locus < LOCUS_COUNT; locus++) {
            state = mix64(state ^ (long) locus * 0x632BE59BD9B4E019L);
            int spread = switch (locus) {
                case 0 -> 1;
                case 2 -> 0;
                case 3, 4, 5, 6, 7, 8 -> 18;
                default -> 1_250;
            };
            int offset = spread == 0 ? 0 : (int) Long.remainderUnsigned(state, spread * 2L + 1L) - spread;
            result.add(Math.clamp(bases[locus] + offset, 0, MAX_ALLELES[locus]));
        }
        return result;
    }

    private static List<Long> mergeAncestors(Data alpha, Data beta) {
        int maximum = Math.max(4, PatinaRules.INSTANCE.maximumGeneticAncestors);
        LinkedHashSet<Long> ancestors = new LinkedHashSet<>();
        ancestors.add(alpha.lineageSignature());
        ancestors.add(beta.lineageSignature());
        ancestors.addAll(alpha.ancestors());
        ancestors.addAll(beta.ancestors());
        ArrayList<Long> result = new ArrayList<>(Math.min(maximum, ancestors.size()));
        for (long ancestor : ancestors) {
            if (ancestor == 0L) continue;
            result.add(ancestor);
            if (result.size() >= maximum) break;
        }
        return List.copyOf(result);
    }

    private static int inbreeding(Data alpha, Data beta) {
        Set<Long> alphaAncestors = new LinkedHashSet<>(alpha.ancestors());
        Set<Long> betaAncestors = new LinkedHashSet<>(beta.ancestors());
        alphaAncestors.add(alpha.lineageSignature());
        betaAncestors.add(beta.lineageSignature());
        int common = 0;
        for (long ancestor : alphaAncestors) if (ancestor != 0L && betaAncestors.contains(ancestor)) common++;
        int denominator = Math.max(1, Math.min(alphaAncestors.size(), betaAncestors.size()));
        return Math.clamp(common * 1_000L / denominator, 0, 1_000);
    }

    private static int heterozygosity(List<Integer> alpha, List<Integer> beta) {
        int different = 0;
        for (int locus = 0; locus < LOCUS_COUNT; locus++) if (!alpha.get(locus).equals(beta.get(locus))) different++;
        return different * 1_000 / LOCUS_COUNT;
    }

    private static int genotypeColor(Data data) {
        int red = (allele(data, 3, false) + allele(data, 3, true) + allele(data, 6, false) + allele(data, 6, true)) / 4;
        int green = (allele(data, 4, false) + allele(data, 4, true) + allele(data, 7, false) + allele(data, 7, true)) / 4;
        int blue = (allele(data, 5, false) + allele(data, 5, true) + allele(data, 8, false) + allele(data, 8, true)) / 4;
        return red << 16 | green << 8 | blue;
    }

    private static int weighted(int genotype, int imprint, int strengthPermille) {
        return (genotype * (1_000 - strengthPermille) + imprint * strengthPermille) / 1_000;
    }

    private static int blendColor(int genotype, int imprint, int strengthPermille) {
        int red = weighted(genotype >>> 16 & 0xFF, imprint >>> 16 & 0xFF, strengthPermille);
        int green = weighted(genotype >>> 8 & 0xFF, imprint >>> 8 & 0xFF, strengthPermille);
        int blue = weighted(genotype & 0xFF, imprint & 0xFF, strengthPermille);
        return red << 16 | green << 8 | blue;
    }

    private static int averageColor(int first, int second) {
        int red = ((first >>> 16 & 0xFF) + (second >>> 16 & 0xFF)) / 2;
        int green = ((first >>> 8 & 0xFF) + (second >>> 8 & 0xFF)) / 2;
        int blue = ((first & 0xFF) + (second & 0xFF)) / 2;
        return red << 16 | green << 8 | blue;
    }

    private static int variantColor(@Nullable ItemVariantData data) {
        if (data == null) return 0xFFFFFF;
        if (data.customColor() != null) return data.customColor();
        DyeColor dye = data.dyeColor();
        return dye == null ? 0xFFFFFF : dye.getTextureDiffuseColor() & 0xFFFFFF;
    }

    private static int stage(@Nullable ItemVariantData data) {
        return data == null ? 0 : data.stage().ordinal();
    }

    private static int allele(Data data, int locus, boolean beta) {
        return (beta ? data.homologBeta() : data.homologAlpha()).get(locus);
    }

    private static String chromosomeNotation(Data data, int chromosome) {
        int start = CHROMOSOME_STARTS[chromosome];
        int end = CHROMOSOME_STARTS[chromosome + 1];
        StringBuilder result = new StringBuilder();
        for (int locus = start; locus < end; locus++) {
            if (!result.isEmpty()) result.append(';');
            result.append(LOCUS_NAMES[locus]).append('^').append(allele(data, locus, false)).append('/')
                .append(LOCUS_NAMES[locus]).append('^').append(allele(data, locus, true));
        }
        return result.toString();
    }

    private static long genomeFingerprint(List<Integer> alpha, List<Integer> beta, long seed, int generation) {
        long result = mix64(seed ^ generation);
        for (int locus = 0; locus < LOCUS_COUNT; locus++) {
            result = mix64(result ^ ((long) alpha.get(locus) << 32) ^ beta.get(locus) ^ (long) locus * 0x9E3779B97F4A7C15L);
        }
        return result;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static Data normalized(Data data) {
        List<Integer> alpha = normalizeHaplotype(data.homologAlpha());
        List<Integer> beta = normalizeHaplotype(data.homologBeta());
        return new Data(Math.max(1, data.schemaVersion()), Math.max(1, data.generation()), data.lineageSignature(), data.parentAlpha(),
            data.parentBeta(), alpha, beta, List.copyOf(data.ancestors()), Math.max(0, data.recombinations()), Math.max(0, data.mutations()),
            Math.clamp(data.heterozygosityPermille(), 0, 1_000), Math.clamp(data.inbreedingPermille(), 0, 1_000),
            Math.clamp(data.imprintOxidationPermille(), 0, 3_000), data.imprintColor() & 0xFFFFFF,
            Math.clamp(data.imprintWaxPermille(), 0, 1_000), Math.clamp(data.imprintStrengthPermille(), 0, 1_000));
    }

    private static List<Integer> normalizeHaplotype(List<Integer> values) {
        ArrayList<Integer> result = new ArrayList<>(LOCUS_COUNT);
        for (int locus = 0; locus < LOCUS_COUNT; locus++) {
            int value = locus < values.size() ? values.get(locus) : DEFAULT_HAPLOTYPE.get(locus);
            result.add(Math.clamp(value, 0, MAX_ALLELES[locus]));
        }
        return List.copyOf(result);
    }

    public record Data(int schemaVersion, int generation, long lineageSignature, long parentAlpha, long parentBeta,
                       List<Integer> homologAlpha, List<Integer> homologBeta, List<Long> ancestors, int recombinations,
                       int mutations, int heterozygosityPermille, int inbreedingPermille, int imprintOxidationPermille,
                       int imprintColor, int imprintWaxPermille, int imprintStrengthPermille) {
        public Data {
            schemaVersion = Math.max(1, schemaVersion);
            generation = Math.max(1, generation);
            homologAlpha = normalizeHaplotype(homologAlpha);
            homologBeta = normalizeHaplotype(homologBeta);
            ancestors = List.copyOf(ancestors);
            recombinations = Math.max(0, recombinations);
            mutations = Math.max(0, mutations);
            heterozygosityPermille = Math.clamp(heterozygosityPermille, 0, 1_000);
            inbreedingPermille = Math.clamp(inbreedingPermille, 0, 1_000);
            imprintOxidationPermille = Math.clamp(imprintOxidationPermille, 0, 3_000);
            imprintColor &= 0xFFFFFF;
            imprintWaxPermille = Math.clamp(imprintWaxPermille, 0, 1_000);
            imprintStrengthPermille = Math.clamp(imprintStrengthPermille, 0, 1_000);
        }
    }

    private record Gamete(List<Integer> alleles, int recombinations, int mutations) {}
}
