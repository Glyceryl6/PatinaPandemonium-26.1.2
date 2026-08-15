package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.advancement.VariantAdvancements;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

public class SeededBrewingCauldronBlockEntity extends BlockEntity {

    public static final int MAX_EFFECTS = 8;
    public static final int MAX_AMPLIFIER = 7;
    public static final int MAX_DURATION = 18_000;
    public static final int BREW_TICKS = 20;
    private static final long BASE_SEED = 0x6A09E667F3BCC909L;
    private static final long STEP_SALT = 0x9E3779B97F4A7C15L;
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int BASE_COLOR = 0x5D235A;

    private int liquidLevel;
    private boolean primed;
    private long seed;
    private int ingredientCount;
    private int brewTicks;
    private boolean variantIngredient;
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

    public void fill() {
        this.liquidLevel = 3;
        this.resetRecipe();
        this.previewColor = WATER_COLOR;
        this.markUpdated();
    }

    public boolean prime(ServerPlayer player) {
        if (this.liquidLevel <= 0 || this.primed || this.brewing()) return false;
        this.primed = true;
        this.seed = mix64(BASE_SEED ^ hashString(BuiltInRegistries.ITEM.getKey(Items.NETHER_WART).toString()));
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
        this.brewTicks = BREW_TICKS;
        this.previewColor = this.computeContents().getColor();
        this.markUpdated();
        return true;
    }

    public ItemStack bottle(ServerPlayer player) {
        if (!this.ready()) return ItemStack.EMPTY;
        PotionContents contents = this.computeContents();
        ItemStack result = new ItemStack(Items.POTION);
        result.set(DataComponents.POTION_CONTENTS, contents);
        result.set(DataComponents.ITEM_NAME, Component.translatable("item.patina_pandemonium.seeded_potion"));
        result.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), new SeededBrewData(this.seed, this.ingredientCount));
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
        if (blockEntity.brewTicks <= 0) return;
        blockEntity.brewTicks--;
        if ((blockEntity.brewTicks & 3) == 0 && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                    1, 0.18D, 0.04D, 0.18D, 0.0D);
        }

        if (blockEntity.brewTicks == 0) blockEntity.markUpdated();
        else blockEntity.setChanged();
    }

    private PotionContents computeContents() {
        if (!this.primed || this.ingredientCount <= 0) {
            return new PotionContents(Optional.empty(), Optional.of(this.primed ? BASE_COLOR : WATER_COLOR), List.of(), Optional.empty());
        }

        int desired = 1 + Math.min(MAX_EFFECTS - 1, Long.numberOfTrailingZeros(mix64(this.seed ^ 0x243F6A8885A308D3L)));
        PriorityQueue<EffectCandidate> selected = new PriorityQueue<>(desired, (first, second) -> Long.compareUnsigned(first.score(), second.score()));
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(holder -> {
            Identifier id = holder.key().identifier();
            long score = mix64(this.seed ^ hashString(id.toString()));
            EffectCandidate candidate = new EffectCandidate(holder, score);
            if (selected.size() < desired) {
                selected.add(candidate);
            } else if (Long.compareUnsigned(score, selected.peek().score()) > 0) {
                selected.poll();
                selected.add(candidate);
            }
        });

        ArrayList<EffectCandidate> ordered = new ArrayList<>(selected);
        ordered.sort((first, second) -> Long.compareUnsigned(second.score(), first.score()));
        ArrayList<MobEffectInstance> effects = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            EffectCandidate candidate = ordered.get(index);
            long parameters = mix64(this.seed ^ candidate.score() ^ STEP_SALT * (index + 1L));
            int amplifier = Math.min(MAX_AMPLIFIER, Long.numberOfTrailingZeros(parameters | 1L << MAX_AMPLIFIER));
            int duration = 20 * 15 * (1 + (int) Math.floorMod(parameters >>> 8, 60L));
            duration = Math.min(MAX_DURATION, duration);
            if (candidate.effect().value().isInstantenous()) duration = 1;
            effects.add(new MobEffectInstance(candidate.effect(), duration, amplifier));
        }

        PotionContents uncolored = new PotionContents(Optional.empty(), Optional.empty(), effects, Optional.empty());
        return new PotionContents(Optional.empty(), Optional.of(uncolored.getColor()), effects, Optional.empty());
    }

    private static long ingredientIdentity(ItemStack stack) {
        long hash = hashString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null && blockData.customColor() != null) {
            hash = mix64(hash ^ hashString(blockData.sourceId().toString()));
            hash = mix64(hash ^ blockData.stage().ordinal() * 31L ^ (blockData.waxed() ? 1L : 0L) ^ blockData.form().ordinal() * 131L);
            hash = mix64(hash ^ blockData.dyeId() * 257L ^ colorIdentity(blockData.customColor()));
        }

        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null && itemData.customColor() != null) {
            hash = mix64(hash ^ hashString(itemData.sourceId().toString()));
            hash = mix64(hash ^ itemData.stage().ordinal() * 31L ^ (itemData.waxed() ? 1L : 0L));
            hash = mix64(hash ^ itemData.dyeId() * 257L ^ colorIdentity(itemData.customColor()));
            if (itemData.modelId() != null) hash = mix64(hash ^ hashString(itemData.modelId().toString()));
        }

        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        if (chemistry != null) hash = mix64(hash ^ chemistry.signature() ^ (long) chemistry.generation() << 32 ^ chemistry.topology());
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        if (provenance != null) hash = mix64(hash ^ provenance.rootFingerprint());
        SeededBrewData brew = stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        if (brew != null) hash = mix64(hash ^ brew.seed() ^ STEP_SALT * brew.ingredientCount());
        return hash;
    }

    private static boolean isVariantIngredient(ItemStack stack) {
        return stack.has(DynamicVariantRegistry.VARIANT_DATA.get()) || DynamicVariantRegistry.peekItemData(stack) != null
            || stack.has(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get()) || VariantProvenance.get(stack) != null
            || stack.has(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
    }

    private static long colorIdentity(Integer color) {
        return 0x1000000L | color.longValue();
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

    private record EffectCandidate(Holder<MobEffect> effect, long score) {}

}