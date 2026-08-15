package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.SeededBrewData;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantProvenance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

public class SeededBrewingCauldronBlockEntity extends BlockEntity {

    public static final int MAX_EFFECTS = 8;
    public static final int BREW_TICKS = 20;
    private static final long BASE_SEED = 0x6A09E667F3BCC909L;
    private static final long STEP_SALT = 0x9E3779B97F4A7C15L;
    private static final long BOTTLE_SALT = 0xD1B54A32D192ED03L;
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int BASE_COLOR = 0x5D235A;
    private static final int DURATION_STEP = 20 * 15;
    private static final List<EffectAffinity> VANILLA_EFFECT_AFFINITIES = List.of(
        new EffectAffinity(Items.SUGAR, List.of(MobEffects.SPEED)),
        new EffectAffinity(Items.RABBIT_FOOT, List.of(MobEffects.JUMP_BOOST)),
        new EffectAffinity(Items.BLAZE_POWDER, List.of(MobEffects.STRENGTH)),
        new EffectAffinity(Items.GLISTERING_MELON_SLICE, List.of(MobEffects.INSTANT_HEALTH)),
        new EffectAffinity(Items.SPIDER_EYE, List.of(MobEffects.POISON)),
        new EffectAffinity(Items.GHAST_TEAR, List.of(MobEffects.REGENERATION)),
        new EffectAffinity(Items.MAGMA_CREAM, List.of(MobEffects.FIRE_RESISTANCE)),
        new EffectAffinity(Items.PUFFERFISH, List.of(MobEffects.WATER_BREATHING)),
        new EffectAffinity(Items.GOLDEN_CARROT, List.of(MobEffects.NIGHT_VISION)),
        new EffectAffinity(Items.TURTLE_HELMET, List.of(MobEffects.SLOWNESS, MobEffects.RESISTANCE)),
        new EffectAffinity(Items.PHANTOM_MEMBRANE, List.of(MobEffects.SLOW_FALLING)),
        new EffectAffinity(Items.FERMENTED_SPIDER_EYE,
                List.of(MobEffects.WEAKNESS, MobEffects.INVISIBILITY,
                        MobEffects.INSTANT_DAMAGE, MobEffects.SLOWNESS)),
        new EffectAffinity(Items.BREEZE_ROD, List.of(MobEffects.WIND_CHARGED)),
        new EffectAffinity(Items.COBWEB, List.of(MobEffects.WEAVING)),
        new EffectAffinity(Items.SLIME_BLOCK, List.of(MobEffects.OOZING)),
        new EffectAffinity(Items.STONE, List.of(MobEffects.INFESTED)));

    private int liquidLevel;
    private boolean primed;
    private long seed;
    private int ingredientCount;
    private int brewTicks;
    private boolean variantIngredient;
    private long affinityFlags;
    private int redstoneCount;
    private int glowstoneCount;
    private PotionForm potionForm = PotionForm.DRINKABLE;
    private int previewColor = WATER_COLOR;

    public SeededBrewingCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(DynamicVariantRegistry.SEEDED_BREWING_CAULDRON_BLOCK_ENTITY.get(), pos, state);
    }

    public int liquidLevel() {
        return this.liquidLevel;
    }

    public boolean primed() {
        return this.primed;
    }

    public boolean brewing() {
        return this.brewTicks > 0;
    }

    public boolean ready() {
        return this.liquidLevel > 0 && this.primed && this.ingredientCount > 0 && !this.brewing();
    }

    public int previewColor() {
        return this.previewColor;
    }

    public void fill(ItemStack waterStack) {
        this.liquidLevel = 3;
        this.resetRecipe();
        this.seed = mix64(BASE_SEED ^ ingredientIdentity(waterStack) ^ this.cauldronIdentity());
        this.previewColor = WATER_COLOR;
        this.markUpdated();
    }

    public boolean prime(ServerPlayer player, ItemStack netherWart) {
        if (this.liquidLevel <= 0 || this.primed || this.brewing()) return false;
        this.primed = true;
        this.seed = mix64(this.seed ^ ingredientIdentity(netherWart) ^ STEP_SALT);
        this.variantIngredient |= isVariantIngredient(netherWart);
        this.previewColor = BASE_COLOR;
        this.markUpdated();
        VariantAdvancements.interaction(player, VariantAdvancements.Metric.BREWING_PRIMED);
        return true;
    }

    public boolean addIngredient(ItemStack stack) {
        if (this.liquidLevel <= 0 || !this.primed || this.brewing() || stack.isEmpty()) return false;
        long identity = ingredientIdentity(stack);
        this.seed = mix64(this.seed ^ identity ^ STEP_SALT * (this.ingredientCount + 1L));
        this.ingredientCount++;
        this.variantIngredient |= isVariantIngredient(stack);
        this.applyIngredientSemantics(sourceItem(stack));
        this.brewTicks = BREW_TICKS;
        this.previewColor = this.computeContents(this.seed).getColor();
        this.markUpdated();
        return true;
    }

    public ItemStack bottle(ServerPlayer player, ItemStack bottleStack) {
        if (!this.ready()) return ItemStack.EMPTY;
        long bottledSeed = mix64(this.seed ^ ingredientIdentity(bottleStack) ^ BOTTLE_SALT);
        PotionContents contents = this.computeContents(bottledSeed);
        ItemStack result = new ItemStack(this.potionForm.item());
        result.set(DataComponents.POTION_CONTENTS, contents);
        result.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), new SeededBrewData(
                bottledSeed, this.ingredientCount, this.redstoneCount, this.glowstoneCount));
        ItemVariantData bottleVariant = DynamicVariantRegistry.peekItemData(bottleStack);
        Identifier potionId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (bottleVariant != null) {
            ItemVariantData potionVariant = new ItemVariantData(
                    potionId, bottleVariant.stage(), bottleVariant.waxed(),
                    bottleVariant.dyeColor(), potionId, bottleVariant.customColor());
            result.set(DynamicVariantRegistry.ITEM_VARIANT_DATA.get(), potionVariant);
            result.set(DataComponents.ITEM_MODEL, DynamicVariantRegistry.VARIANT_ITEM_MODEL);
            result.set(DataComponents.ITEM_NAME, DynamicVariantRegistry.variantItemName(result, potionVariant));
        } else {
            result.set(DataComponents.ITEM_NAME, Component.translatable("item." + PatinaPandemonium.MOD_ID + ".seeded_" + potionId.getPath()));
        }

        VariantAdvancements.evaluateBrewing(player, this.ingredientCount, contents.customEffects(), this.variantIngredient);
        this.liquidLevel--;
        if (this.liquidLevel <= 0) {
            this.liquidLevel = 0;
            this.resetRecipe();
            this.previewColor = WATER_COLOR;
        }
        this.markUpdated();
        return result;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeededBrewingCauldronBlockEntity blockEntity) {
        if (blockEntity.brewTicks <= 0 || level.isClientSide()) return;
        blockEntity.brewTicks--;
        if ((blockEntity.brewTicks & 3) == 0 && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                    1, 0.18D, 0.04D, 0.18D, 0.0D);
        }

        if (blockEntity.brewTicks == 0) blockEntity.markUpdated();
        else blockEntity.setChanged();
    }

    private PotionContents computeContents(long effectiveSeed) {
        if (!this.primed || this.ingredientCount <= 0) {
            return new PotionContents(Optional.empty(), Optional.of(this.primed ? BASE_COLOR : WATER_COLOR), List.of(), Optional.empty());
        }

        int desired = 1 + Math.min(MAX_EFFECTS - 1, Long.numberOfTrailingZeros(mix64(effectiveSeed ^ 0x243F6A8885A308D3L)));
        PriorityQueue<EffectCandidate> selected = new PriorityQueue<>(desired, Comparator.comparingDouble(EffectCandidate::priority).reversed());
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(holder -> {
            Identifier id = holder.key().identifier();
            long entropy = mix64(effectiveSeed ^ hashString(id.toString()));
            int weight = this.effectWeight(holder);
            double unit = ((entropy >>> 11) + 1.0D) * 0x1.0p-53;
            EffectCandidate candidate = new EffectCandidate(holder, entropy, -Math.log(unit) / weight);
            if (selected.peek() != null && candidate.priority() < selected.peek().priority()) {
                selected.poll();
                selected.add(candidate);
            } else if (selected.size() < desired) {
                selected.add(candidate);
            }
        });

        ArrayList<EffectCandidate> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparingDouble(EffectCandidate::priority));
        ArrayList<MobEffectInstance> effects = new ArrayList<>(ordered.size());
        PatinaRules rules = PatinaRules.INSTANCE;
        int durationSteps = Math.max(1, rules.brewingBaseMaximumDuration / DURATION_STEP);
        for (int index = 0; index < ordered.size(); index++) {
            EffectCandidate candidate = ordered.get(index);
            long parameters = mix64(effectiveSeed ^ candidate.entropy() ^ STEP_SALT * (index + 1L));
            int baseAmplifier = Math.min(rules.brewingBaseMaximumAmplifier,
                Long.numberOfTrailingZeros(parameters | 1L << rules.brewingBaseMaximumAmplifier));
            long amplified = (long) baseAmplifier + (long) this.glowstoneCount * rules.brewingGlowstoneAmplifierBonus;
            int amplifier = (int) Math.min(rules.brewingMaximumAmplifier, amplified);
            int baseDuration = DURATION_STEP * (1 + Math.floorMod(parameters >>> 8, durationSteps));
            double durationMultiplier = 1.0D + this.redstoneCount * rules.brewingRedstoneDurationBonus;
            int duration = (int) Math.min(rules.brewingMaximumDuration, Math.round(baseDuration * durationMultiplier));
            if (candidate.effect().value().isInstantenous()) duration = 1;
            effects.add(new MobEffectInstance(candidate.effect(), duration, amplifier));
        }

        PotionContents uncolored = new PotionContents(Optional.empty(), Optional.empty(), effects, Optional.empty());
        return new PotionContents(Optional.empty(), Optional.of(uncolored.getColor()), effects, Optional.empty());
    }

    private void applyIngredientSemantics(Item source) {
        for (int index = 0; index < VANILLA_EFFECT_AFFINITIES.size(); index++) {
            if (VANILLA_EFFECT_AFFINITIES.get(index).item() == source) this.affinityFlags |= 1L << index;
        }

        if (source == Items.REDSTONE && this.redstoneCount < Integer.MAX_VALUE) this.redstoneCount++;
        if (source == Items.GLOWSTONE_DUST && this.glowstoneCount < Integer.MAX_VALUE) this.glowstoneCount++;
        if (source == Items.GUNPOWDER && this.potionForm.ordinal() < PotionForm.SPLASH.ordinal()) this.potionForm = PotionForm.SPLASH;
        if (source == Items.DRAGON_BREATH) this.potionForm = PotionForm.LINGERING;
    }

    private int effectWeight(Holder<MobEffect> effect) {
        int weight = 1;
        for (int index = 0; index < VANILLA_EFFECT_AFFINITIES.size(); index++) {
            if ((this.affinityFlags & 1L << index) == 0L) continue;
            if (VANILLA_EFFECT_AFFINITIES.get(index).effects().contains(effect)) {
                weight += PatinaRules.INSTANCE.brewingVanillaAffinityWeight;
            }
        }

        return weight;
    }

    private long cauldronIdentity() {
        long hash = hashString(BuiltInRegistries.BLOCK.getKey(this.getBlockState().getBlock()).toString());
        VariantData variant = DynamicVariantRegistry.blockEntityVariantData(this);
        if (variant != null) hash = mix64(hash ^ variantIdentity(variant));
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(this);
        if (chemistry != null) hash = mix64(hash ^ chemistryIdentity(chemistry));
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(this);
        if (provenance != null) hash = mix64(hash ^ provenance.rootFingerprint());
        return hash;
    }

    private static long ingredientIdentity(ItemStack stack) {
        long hash = hashString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null) hash = mix64(hash ^ variantIdentity(blockData));
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null && itemData.customColor() != null) {
            hash = mix64(hash ^ hashString(itemData.sourceId().toString()));
            hash = mix64(hash ^ itemData.stage().ordinal() * 31L ^ (itemData.waxed() ? 1L : 0L));
            hash = mix64(hash ^ itemData.dyeId() * 257L ^ colorIdentity(itemData.customColor()));
            if (itemData.modelId() != null) hash = mix64(hash ^ hashString(itemData.modelId().toString()));
        }

        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        if (chemistry != null) hash = mix64(hash ^ chemistryIdentity(chemistry));
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        if (provenance != null) hash = mix64(hash ^ provenance.rootFingerprint());
        SeededBrewData brew = stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        if (brew != null) {
            hash = mix64(hash ^ brew.seed() ^ STEP_SALT * brew.ingredientCount());
            hash = mix64(hash ^ (long) brew.redstoneCount() << 32 ^ brew.glowstoneCount());
        }

        return hash;
    }

    private static Item sourceItem(ItemStack stack) {
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null) {
            Item source = BuiltInRegistries.ITEM.getValue(itemData.sourceId());
            if (source != Items.AIR) return source;
        }

        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null) {
            Item source = BuiltInRegistries.BLOCK.getValue(blockData.sourceId()).asItem();
            if (source != Items.AIR) return source;
        }

        return stack.getItem();
    }

    private static boolean isVariantIngredient(ItemStack stack) {
        return stack.has(DynamicVariantRegistry.VARIANT_DATA.get())
                || DynamicVariantRegistry.peekItemData(stack) != null
                || stack.has(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get())
                || VariantProvenance.get(stack) != null
                || stack.has(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
    }

    private static long variantIdentity(VariantData data) {
        long hash = hashString(data.sourceId().toString());
        hash = mix64(hash ^ data.stage().ordinal() * 31L ^ (data.waxed() ? 1L : 0L) ^ data.form().ordinal() * 131L);
        return mix64(hash ^ data.dyeId() * 257L ^ colorIdentity(data.customColor()));
    }

    private static long chemistryIdentity(CraftingChemistry.Data data) {
        return mix64(data.signature() ^ (long) data.generation() << 32 ^ data.topology());
    }

    private static long colorIdentity(@Nullable Integer color) {
        return color == null ? 0L : 0x1000000L | color.longValue();
    }

    private static long hashString(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private void resetRecipe() {
        this.primed = false;
        this.seed = 0L;
        this.ingredientCount = 0;
        this.brewTicks = 0;
        this.variantIngredient = false;
        this.affinityFlags = 0L;
        this.redstoneCount = 0;
        this.glowstoneCount = 0;
        this.potionForm = PotionForm.DRINKABLE;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level == null) return;
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.liquidLevel = Math.clamp(input.getIntOr("liquid_level", 0), 0, 3);
        this.primed = input.getBooleanOr("primed", false);
        this.seed = input.getLongOr("seed", 0L);
        this.ingredientCount = Math.max(0, input.getIntOr("ingredients", 0));
        this.brewTicks = Math.max(0, input.getIntOr("brew_ticks", 0));
        this.variantIngredient = input.getBooleanOr("variant_ingredient", false);
        this.affinityFlags = input.getLongOr("affinity_flags", 0L);
        this.redstoneCount = Math.max(0, input.getIntOr("redstone_count", 0));
        this.glowstoneCount = Math.max(0, input.getIntOr("glowstone_count", 0));
        this.potionForm = PotionForm.byOrdinal(input.getIntOr("potion_form", 0));
        this.previewColor = input.getIntOr("preview_color", this.primed ? BASE_COLOR : WATER_COLOR) & 0xFFFFFF;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("liquid_level", this.liquidLevel);
        output.putBoolean("primed", this.primed);
        output.putLong("seed", this.seed);
        output.putInt("ingredients", this.ingredientCount);
        output.putInt("brew_ticks", this.brewTicks);
        output.putBoolean("variant_ingredient", this.variantIngredient);
        output.putLong("affinity_flags", this.affinityFlags);
        output.putInt("redstone_count", this.redstoneCount);
        output.putInt("glowstone_count", this.glowstoneCount);
        output.putInt("potion_form", this.potionForm.ordinal());
        output.putInt("preview_color", this.previewColor);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private record EffectAffinity(Item item, List<Holder<MobEffect>> effects) {}

    private record EffectCandidate(Holder<MobEffect> effect, long entropy, double priority) {}

    private enum PotionForm {
        DRINKABLE(Items.POTION),
        SPLASH(Items.SPLASH_POTION),
        LINGERING(Items.LINGERING_POTION);

        private final Item item;

        PotionForm(Item item) {
            this.item = item;
        }

        private Item item() {
            return this.item;
        }

        private static PotionForm byOrdinal(int ordinal) {
            PotionForm[] values = values();
            return values[Math.clamp(ordinal, 0, values.length - 1)];
        }
    }

}
