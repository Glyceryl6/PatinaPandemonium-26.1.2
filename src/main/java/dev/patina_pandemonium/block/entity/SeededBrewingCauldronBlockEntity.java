package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
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

import java.util.*;

public class SeededBrewingCauldronBlockEntity extends BlockEntity {

    private static final int BREW_TICKS = 20;
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int BASE_COLOR = 0x5D235A;
    private static final long BASE_SEED = 0x6A09E667F3BCC909L;
    private static final long STEP_SALT = 0x9E3779B97F4A7C15L;
    private static final long BOTTLE_SALT = 0xBB67AE8584CAA73BL;
    private static final int BASE_EFFECT_COST = 6;
    private static final int DURATION_PER_BUDGET = 40;

    private int liquidLevel;
    private boolean primed;
    private int brewTicks;
    private boolean variantIngredient;
    private int colorFrom = WATER_COLOR;
    private int colorTo = WATER_COLOR;
    private int colorTransitionTicks;
    private int colorTransitionDuration;
    private NonNullList<ItemStack> batchMemory = NonNullList.withSize(1, ItemStack.EMPTY);

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
        return this.liquidLevel > 0 && this.primed && this.brewData().ingredientCount() > 0 && !this.brewing();
    }

    public int previewColor() {
        if (this.colorTransitionTicks <= 0 || this.colorTransitionDuration <= 0) return this.colorTo;
        double progress = 1.0D - (double) this.colorTransitionTicks / this.colorTransitionDuration;
        return lerpColor(this.colorFrom, this.colorTo, progress);
    }

    public void fill(ItemStack water) {
        this.liquidLevel = 3;
        this.resetRecipe();
        ItemStack cauldron = this.cauldronStack();
        long seed = mix64(BASE_SEED ^ ingredientIdentity(cauldron) ^ Long.rotateLeft(ingredientIdentity(water), 17));
        ArrayList<String> names = new ArrayList<>();
        appendName(names, cauldron.getHoverName().getString());
        appendName(names, water.getHoverName().getString());
        SeededBrewData data = this.withBudget(new SeededBrewData(seed, 0, 1, 0, 0, 0,
            SeededBrewData.DELIVERY_DRINKABLE, 2, 0, List.of(), names));
        ItemStack memory = new ItemStack(Items.POTION);
        memory.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), data);
        memory.set(DynamicVariantRegistry.PROVENANCE.get(), VariantProvenance.brewStart(cauldron, water,
            VariantProvenance.attributes("stage", "fill", "seed", Long.toUnsignedString(seed, 16))));
        this.batchMemory.set(0, memory);
        this.variantIngredient = isVariantIngredient(cauldron) || isVariantIngredient(water);
        this.setColorImmediate(WATER_COLOR);
        this.markUpdated();
    }

    public boolean prime(ServerPlayer player, ItemStack netherWart) {
        if (this.liquidLevel <= 0 || this.primed || this.brewing()) return false;
        SeededBrewData previous = this.brewData();
        long seed = mix64(previous.seed() ^ ingredientIdentity(netherWart) ^ STEP_SALT);
        ArrayList<String> names = new ArrayList<>(previous.nameSegments());
        appendName(names, netherWart.getHoverName().getString());
        SeededBrewData next = this.withBudget(new SeededBrewData(seed, previous.ingredientCount(), previous.generation(), 0,
            previous.redstoneCount(), previous.glowstoneCount(), previous.delivery(), previous.lineageLength() + 1,
            previous.parentCount(), previous.affinities(), names));
        this.setBrewData(next);
        this.setBrewProvenance(VariantProvenance.brewStep(this.brewProvenance(), netherWart, "brew_base",
            VariantProvenance.attributes("base", BuiltInRegistries.ITEM.getKey(netherWart.getItem()), "seed", Long.toUnsignedString(seed, 16))));
        this.primed = true;
        this.variantIngredient |= isVariantIngredient(netherWart);
        this.transitionColor(BASE_COLOR);
        this.markUpdated();
        VariantAdvancements.interaction(player, VariantAdvancements.Metric.BREWING_PRIMED);
        return true;
    }

    public boolean addIngredient(ItemStack stack) {
        if (this.liquidLevel <= 0 || !this.primed || this.brewing() || stack.isEmpty()) return false;
        SeededBrewData previous = this.brewData();
        long seed = mix64(previous.seed() ^ ingredientIdentity(stack) ^ STEP_SALT * (previous.ingredientCount() + 1L));
        int generation = previous.generation();
        int parents = previous.parentCount();
        int lineageLength = previous.lineageLength();
        int redstone = previous.redstoneCount();
        int glowstone = previous.glowstoneCount();
        int delivery = previous.delivery();
        SeededBrewData parent = stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        HashMap<Identifier, Integer> affinities = affinityMap(previous.affinities());
        if (parent != null) {
            generation = Math.max(generation, saturatedIncrement(parent.generation()));
            parents = saturatedAdd(parents, saturatedIncrement(parent.parentCount()));
            lineageLength = saturatedAdd(lineageLength, saturatedIncrement(parent.lineageLength()));
            inheritAffinities(affinities, parent, stack, seed);
        } else {
            lineageLength = saturatedIncrement(lineageLength);
            this.applyTaggedAffinities(stack, affinities);
        }

        if (stack.is(Items.REDSTONE)) redstone = saturatedIncrement(redstone);
        if (stack.is(Items.GLOWSTONE_DUST)) glowstone = saturatedIncrement(glowstone);
        if (stack.is(Items.GUNPOWDER)) delivery = Math.max(delivery, SeededBrewData.DELIVERY_SPLASH);
        if (stack.is(Items.DRAGON_BREATH)) delivery = SeededBrewData.DELIVERY_LINGERING;
        ArrayList<String> names = new ArrayList<>(previous.nameSegments());
        appendName(names, stack.getHoverName().getString());
        SeededBrewData next = this.withBudget(new SeededBrewData(seed, saturatedIncrement(previous.ingredientCount()), generation, 0,
            redstone, glowstone, delivery, lineageLength, parents, compactAffinities(affinities), names));
        this.setBrewData(next);
        this.setBrewProvenance(VariantProvenance.brewStep(this.brewProvenance(), stack, operation(stack, parent),
            VariantProvenance.attributes("step", next.ingredientCount(), "generation", next.generation(), "potency_budget", next.potencyBudget(),
                "seed", Long.toUnsignedString(next.seed(), 16), "delivery", next.delivery())));
        this.variantIngredient |= isVariantIngredient(stack);
        this.brewTicks = BREW_TICKS;
        this.transitionColor(this.computeContents(next).getColor());
        this.markUpdated();
        return true;
    }

    public ItemStack bottle(ServerPlayer player, ItemStack bottle) {
        if (!this.ready()) return ItemStack.EMPTY;
        SeededBrewData batch = this.brewData();
        long plainBottle = hashString(BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE).toString());
        long bottleDelta = ingredientIdentity(bottle) ^ plainBottle;
        long seed = bottleDelta == 0L ? batch.seed() : mix64(batch.seed() ^ bottleDelta ^ BOTTLE_SALT);
        ArrayList<String> names = new ArrayList<>(batch.nameSegments());
        appendName(names, bottle.getHoverName().getString());
        SeededBrewData finalData = this.withBudget(new SeededBrewData(seed, batch.ingredientCount(), batch.generation(), 0,
            batch.redstoneCount(), batch.glowstoneCount(), batch.delivery(), saturatedIncrement(batch.lineageLength()),
            batch.parentCount(), batch.affinities(), names));
        PotionContents contents = this.computeContents(finalData);
        finalData = this.expressAffinities(finalData, contents.customEffects());
        ItemStack result = finalData.outputStack();
        this.applyBottleVariant(bottle, result);
        result.set(DataComponents.POTION_CONTENTS, contents);
        result.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), finalData);
        result.set(DataComponents.ITEM_NAME, Component.translatable(switch (finalData.delivery()) {
            case SeededBrewData.DELIVERY_SPLASH -> "item.patina_pandemonium.seeded_splash_potion";
            case SeededBrewData.DELIVERY_LINGERING -> "item.patina_pandemonium.seeded_lingering_potion";
            default -> "item.patina_pandemonium.seeded_potion";
        }));
        VariantProvenance.brewBottle(this.brewProvenance(), bottle, result, VariantProvenance.attributes(
            "generation", finalData.generation(), "seed", Long.toUnsignedString(finalData.seed(), 16),
            "effects", contents.customEffects().size(), "delivery", finalData.delivery(), "potency_budget", finalData.potencyBudget()));
        VariantAdvancements.evaluateBrewing(player, finalData.ingredientCount(), contents.customEffects(),
            this.variantIngredient || isVariantIngredient(bottle));
        this.liquidLevel--;
        if (this.liquidLevel <= 0) {
            this.liquidLevel = 0;
            this.resetRecipe();
            this.setColorImmediate(WATER_COLOR);
        }
        this.markUpdated();
        return result;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeededBrewingCauldronBlockEntity blockEntity) {
        boolean changed = false;
        if (blockEntity.brewTicks > 0) {
            blockEntity.brewTicks--;
            changed = true;
            if ((blockEntity.brewTicks & 3) == 0 && level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                    1, 0.18D, 0.04D, 0.18D, 0.0D);
            }
            if (blockEntity.brewTicks == 0 && !level.isClientSide()) blockEntity.markUpdated();
        }
        if (blockEntity.colorTransitionTicks > 0) {
            blockEntity.colorTransitionTicks--;
            changed = true;
        }
        if (changed) blockEntity.setChanged();
    }

    private PotionContents computeContents(SeededBrewData data) {
        if (!this.primed || data.ingredientCount() <= 0) {
            return new PotionContents(Optional.empty(), Optional.of(this.primed ? BASE_COLOR : WATER_COLOR), List.of(), Optional.empty());
        }

        PatinaRules rules = PatinaRules.INSTANCE;
        int tier = generationTier(data.generation());
        int effectCap = Math.min(rules.brewingMaximumEffects, tier);
        int desired = 1 + Math.floorMod((int) mix64(data.seed() ^ 0x243F6A8885A308D3L), Math.max(1, effectCap));
        HashMap<Identifier, Integer> affinities = affinityMap(data.affinities());
        ArrayList<EffectCandidate> candidates = new ArrayList<>();
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(holder -> {
            Identifier id = holder.key().identifier();
            long mixed = mix64(data.seed() ^ hashString(id.toString()));
            double score = unit(mixed) * (1.0D + affinities.getOrDefault(id, 0));
            candidates.add(new EffectCandidate(holder, id, score, mixed));
        });
        candidates.sort(Comparator.comparingDouble(EffectCandidate::score).reversed()
            .thenComparing(candidate -> candidate.id().toString()));
        if (candidates.size() > desired) candidates.subList(desired, candidates.size()).clear();

        int budget = Math.max(1, data.potencyBudget());
        int share = Math.max(1, budget / Math.max(1, candidates.size()));
        int amplifierCap = Math.clamp((tier - 1) / 2, 0, rules.brewingMaximumAmplifier);
        int durationCap = durationCap(tier);
        ArrayList<MobEffectInstance> effects = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            EffectCandidate candidate = candidates.get(index);
            long parameters = mix64(data.seed() ^ candidate.mixed() ^ STEP_SALT * (index + 1L));
            int amplifier = amplifierCap <= 0 ? 0 : (int) Math.min(amplifierCap,
                (long) Math.floor(unit(parameters) * (amplifierCap + 1)) + data.glowstoneCount());
            int amplifierCost = amplifierCost(amplifier);
            int amplifierBudget = Math.max(BASE_EFFECT_COST, share * 2 / 3);
            while (amplifier > 0 && amplifierCost > amplifierBudget) amplifierCost = amplifierCost(--amplifier);
            int durationBudget = Math.max(1, share - amplifierCost);
            double redstoneMultiplier = 1.0D + data.redstoneCount() * rules.brewingRedstoneDurationBonus;
            int duration = (int) Math.clamp(Math.round((200L + (long) durationBudget * DURATION_PER_BUDGET) * redstoneMultiplier), 20L, durationCap);
            if (candidate.effect().value().isInstantenous()) duration = 1;
            effects.add(new MobEffectInstance(candidate.effect(), duration, amplifier));
        }

        PotionContents uncolored = new PotionContents(Optional.empty(), Optional.empty(), effects, Optional.empty());
        return new PotionContents(Optional.empty(), Optional.of(uncolored.getColor()), effects, Optional.empty());
    }

    private SeededBrewData expressAffinities(SeededBrewData data, List<MobEffectInstance> effects) {
        HashMap<Identifier, Integer> affinities = affinityMap(data.affinities());
        int bonus = PatinaRules.INSTANCE.brewingSelectedAffinityBonus;
        for (MobEffectInstance effect : effects) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null) affinities.merge(id, bonus, SeededBrewingCauldronBlockEntity::saturatedAdd);
        }
        return new SeededBrewData(data.seed(), data.ingredientCount(), data.generation(), data.potencyBudget(),
            data.redstoneCount(), data.glowstoneCount(), data.delivery(), data.lineageLength(), data.parentCount(),
            compactAffinities(affinities), data.nameSegments());
    }

    private void applyTaggedAffinities(ItemStack stack, Map<Identifier, Integer> affinities) {
        int weight = PatinaRules.INSTANCE.brewingAffinityWeight;
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(holder -> {
            Identifier effectId = holder.key().identifier();
            if (stack.is(affinityTag(effectId))) affinities.merge(effectId, weight, SeededBrewingCauldronBlockEntity::saturatedAdd);
        });
    }

    private static void inheritAffinities(Map<Identifier, Integer> target, SeededBrewData parent, ItemStack parentStack, long seed) {
        int permille = PatinaRules.INSTANCE.brewingInheritedAffinityPermille;
        for (SeededBrewData.Affinity affinity : parent.affinities()) {
            int inherited = (int) Math.min(Integer.MAX_VALUE, (long) affinity.weight() * permille / 1_000L);
            int mutation = Math.floorMod((int) mix64(seed ^ hashString(affinity.effectId().toString())), 3) - 1;
            inherited = Math.max(0, inherited + mutation);
            if (inherited > 0) target.merge(affinity.effectId(), inherited, SeededBrewingCauldronBlockEntity::saturatedAdd);
        }
        PotionContents contents = parentStack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;
        int phenotypeBonus = Math.max(1, PatinaRules.INSTANCE.brewingAffinityWeight / 2);
        for (MobEffectInstance effect : contents.customEffects()) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null) target.merge(id, phenotypeBonus, SeededBrewingCauldronBlockEntity::saturatedAdd);
        }
    }

    private SeededBrewData withBudget(SeededBrewData data) {
        int tier = generationTier(data.generation());
        PatinaRules rules = PatinaRules.INSTANCE;
        long budget = rules.brewingBasePotencyBudget;
        budget += (long) Math.max(0, tier - 1) * rules.brewingPotencyBudgetPerTier;
        budget += Math.min(rules.brewingIngredientBudgetCap, data.ingredientCount());
        budget += (long) data.glowstoneCount() * rules.brewingGlowstoneBudgetBonus;
        int clamped = (int) Math.clamp(budget, 1L, Integer.MAX_VALUE);
        return new SeededBrewData(data.seed(), data.ingredientCount(), data.generation(), clamped, data.redstoneCount(),
            data.glowstoneCount(), data.delivery(), data.lineageLength(), data.parentCount(), data.affinities(), data.nameSegments());
    }

    private static int durationCap(int tier) {
        PatinaRules rules = PatinaRules.INSTANCE;
        long cap = rules.brewingBaseDuration;
        cap <<= Math.clamp(tier - 1, 0, 20);
        return (int) Math.clamp(cap, 20L, rules.brewingMaximumDuration);
    }

    private static int generationTier(int generation) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(1, generation));
    }

    private static int amplifierCost(int amplifier) {
        return BASE_EFFECT_COST * (1 << Math.clamp(amplifier, 0, 20));
    }

    private static TagKey<Item> affinityTag(Identifier effectId) {
        return TagKey.create(Registries.ITEM, PatinaPandemonium.id("brewing_affinity/" + effectId.getNamespace() + "/" + effectId.getPath()));
    }

    private static HashMap<Identifier, Integer> affinityMap(List<SeededBrewData.Affinity> affinities) {
        HashMap<Identifier, Integer> result = new HashMap<>();
        for (SeededBrewData.Affinity affinity : affinities) {
            if (affinity.weight() > 0) result.merge(affinity.effectId(), affinity.weight(), SeededBrewingCauldronBlockEntity::saturatedAdd);
        }
        return result;
    }

    private static List<SeededBrewData.Affinity> compactAffinities(Map<Identifier, Integer> affinities) {
        return affinities.entrySet().stream().filter(entry -> entry.getValue() > 0)
            .sorted(Map.Entry.<Identifier, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().toString()))
            .limit(PatinaRules.INSTANCE.brewingMaximumAffinityEntries)
            .map(entry -> new SeededBrewData.Affinity(entry.getKey(), entry.getValue())).toList();
    }

    private static String operation(ItemStack stack, @Nullable SeededBrewData parent) {
        if (parent != null) return "brew_parent";
        if (stack.is(Items.REDSTONE)) return "brew_redstone";
        if (stack.is(Items.GLOWSTONE_DUST)) return "brew_glowstone";
        if (stack.is(Items.GUNPOWDER)) return "brew_splash";
        if (stack.is(Items.DRAGON_BREATH)) return "brew_lingering";
        return "brew_reagent";
    }

    private void applyBottleVariant(ItemStack bottle, ItemStack result) {
        ItemVariantData bottleData = DynamicVariantRegistry.peekItemData(bottle);
        if (bottleData == null) return;
        Identifier resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        ItemVariantData resultData = new ItemVariantData(resultId, bottleData.stage(), bottleData.waxed(), bottleData.dyeColor(),
            resultId, bottleData.customColor());
        result.set(DynamicVariantRegistry.ITEM_VARIANT_DATA.get(), resultData);
        result.set(DataComponents.ITEM_MODEL, DynamicVariantRegistry.VARIANT_ITEM_MODEL);
    }

    private ItemStack cauldronStack() {
        ItemStack stack = new ItemStack(DynamicVariantRegistry.SEEDED_BREWING_CAULDRON_ITEM.get());
        VariantData variant = DynamicVariantRegistry.blockEntityVariantData(this);
        if (variant != null) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            stack.set(DynamicVariantRegistry.ITEM_VARIANT_DATA.get(), new ItemVariantData(id, variant.stage(), variant.waxed(),
                variant.dyeColor(), id, variant.customColor()));
            stack.set(DataComponents.ITEM_MODEL, DynamicVariantRegistry.VARIANT_ITEM_MODEL);
        }
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(this);
        if (provenance != null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(this);
        if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
        return stack;
    }

    private SeededBrewData brewData() {
        ItemStack stack = this.batchMemory.getFirst();
        SeededBrewData data = stack.isEmpty() ? null : stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        return data == null ? SeededBrewData.empty() : data;
    }

    private VariantProvenance.Data brewProvenance() {
        ItemStack stack = this.batchMemory.getFirst();
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.POTION);
            this.batchMemory.set(0, stack);
        }
        VariantProvenance.Data data = VariantProvenance.get(stack);
        if (data == null) {
            data = VariantProvenance.ensure(stack);
            stack.set(DynamicVariantRegistry.PROVENANCE.get(), data);
        }
        return data;
    }

    private void setBrewData(SeededBrewData data) {
        ItemStack stack = this.batchMemory.getFirst();
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.POTION);
            this.batchMemory.set(0, stack);
        }
        stack.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), data);
    }

    private void setBrewProvenance(VariantProvenance.Data data) {
        ItemStack stack = this.batchMemory.getFirst();
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.POTION);
            this.batchMemory.set(0, stack);
        }
        stack.set(DynamicVariantRegistry.PROVENANCE.get(), data);
    }

    private void transitionColor(int target) {
        this.colorFrom = this.previewColor();
        this.colorTo = target & 0xFFFFFF;
        this.colorTransitionDuration = PatinaRules.INSTANCE.brewingColorTransitionTicks;
        this.colorTransitionTicks = this.colorTransitionDuration;
    }

    private void setColorImmediate(int color) {
        this.colorFrom = color & 0xFFFFFF;
        this.colorTo = color & 0xFFFFFF;
        this.colorTransitionTicks = 0;
        this.colorTransitionDuration = 0;
    }

    private static int lerpColor(int from, int to, double progress) {
        progress = Math.clamp(progress, 0.0D, 1.0D);
        int red = (int) Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * progress);
        int green = (int) Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * progress);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
        return red << 16 | green << 8 | blue;
    }

    private static void appendName(List<String> names, @Nullable String segment) {
        if (segment == null || segment.isBlank()) return;
        int maximum = PatinaRules.INSTANCE.brewingMaximumNameCharacters;
        int used = names.stream().mapToInt(String::length).sum() + Math.max(0, names.size() - 1);
        int remaining = maximum - used;
        if (remaining <= 0) return;
        names.add(segment.length() <= remaining ? segment : segment.substring(0, remaining));
    }

    private static long ingredientIdentity(ItemStack stack) {
        long hash = hashString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null && blockData.customColor() != null) {
            hash = mix64(hash ^ hashString(blockData.sourceId().toString()));
            hash = mix64(hash ^ blockData.stage().ordinal() * 31L ^ (blockData.waxed() ? 1L : 0L) ^ blockData.form().ordinal() * 131L);
            hash = mix64(hash ^ blockData.dyeId() * 257L ^ optionalColorIdentity(blockData.customColor()));
        }

        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null && itemData.customColor() != null) {
            hash = mix64(hash ^ hashString(itemData.sourceId().toString()));
            hash = mix64(hash ^ itemData.stage().ordinal() * 31L ^ (itemData.waxed() ? 1L : 0L));
            hash = mix64(hash ^ itemData.dyeId() * 257L ^ optionalColorIdentity(itemData.customColor()));
            if (itemData.modelId() != null) hash = mix64(hash ^ hashString(itemData.modelId().toString()));
        }

        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        if (chemistry != null) hash = mix64(hash ^ chemistry.signature() ^ (long) chemistry.generation() << 32 ^ chemistry.topology());
        VariantProvenance.Data provenance = VariantProvenance.get(stack);
        if (provenance != null) hash = mix64(hash ^ provenance.rootFingerprint());
        SeededBrewData brew = stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        if (brew != null) {
            hash = mix64(hash ^ brew.seed() ^ STEP_SALT * brew.ingredientCount());
            hash = mix64(hash ^ (long) brew.generation() << 32 ^ brew.parentCount());
        }
        return hash;
    }

    private static boolean isVariantIngredient(ItemStack stack) {
        return stack.has(DynamicVariantRegistry.VARIANT_DATA.get()) || DynamicVariantRegistry.peekItemData(stack) != null
            || stack.has(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get()) || VariantProvenance.get(stack) != null
            || stack.has(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
    }

    private static long optionalColorIdentity(@Nullable Integer color) {
        return color == null ? 0xD6E8FEB86659FD93L : 0x1000000L | color.longValue();
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
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

    private static int saturatedIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static int saturatedAdd(int first, int second) {
        if (first >= Integer.MAX_VALUE - second) return Integer.MAX_VALUE;
        return first + second;
    }

    private void resetRecipe() {
        this.primed = false;
        this.brewTicks = 0;
        this.variantIngredient = false;
        this.batchMemory.set(0, ItemStack.EMPTY);
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
        this.brewTicks = Math.max(0, input.getIntOr("brew_ticks", 0));
        this.variantIngredient = input.getBooleanOr("variant_ingredient", false);
        int legacyColor = input.getIntOr("preview_color", this.primed ? BASE_COLOR : WATER_COLOR) & 0xFFFFFF;
        this.colorFrom = input.getIntOr("color_from", legacyColor) & 0xFFFFFF;
        this.colorTo = input.getIntOr("color_to", legacyColor) & 0xFFFFFF;
        this.colorTransitionTicks = Math.max(0, input.getIntOr("color_ticks", 0));
        this.colorTransitionDuration = Math.max(this.colorTransitionTicks, input.getIntOr("color_duration", this.colorTransitionTicks));
        this.batchMemory = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.batchMemory);
        if (this.batchMemory.getFirst().isEmpty() && input.getLongOr("seed", 0L) != 0L) {
            long legacySeed = input.getLongOr("seed", 0L);
            int legacyIngredients = Math.max(0, input.getIntOr("ingredients", 0));
            ItemStack memory = new ItemStack(Items.POTION);
            SeededBrewData legacy = this.withBudget(new SeededBrewData(legacySeed, legacyIngredients));
            memory.set(DynamicVariantRegistry.SEEDED_BREW_DATA.get(), legacy);
            memory.set(DynamicVariantRegistry.PROVENANCE.get(), VariantProvenance.ensure(memory));
            this.batchMemory.set(0, memory);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("liquid_level", this.liquidLevel);
        output.putBoolean("primed", this.primed);
        output.putInt("brew_ticks", this.brewTicks);
        output.putBoolean("variant_ingredient", this.variantIngredient);
        output.putInt("preview_color", this.previewColor());
        output.putInt("color_from", this.colorFrom);
        output.putInt("color_to", this.colorTo);
        output.putInt("color_ticks", this.colorTransitionTicks);
        output.putInt("color_duration", this.colorTransitionDuration);
        SeededBrewData data = this.brewData();
        output.putLong("seed", data.seed());
        output.putInt("ingredients", data.ingredientCount());
        ContainerHelper.saveAllItems(output, this.batchMemory);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("liquid_level", this.liquidLevel);
        tag.putBoolean("primed", this.primed);
        tag.putInt("brew_ticks", this.brewTicks);
        tag.putInt("preview_color", this.previewColor());
        tag.putInt("color_from", this.colorFrom);
        tag.putInt("color_to", this.colorTo);
        tag.putInt("color_ticks", this.colorTransitionTicks);
        tag.putInt("color_duration", this.colorTransitionDuration);
        return tag;
    }

    private record EffectCandidate(Holder<MobEffect> effect, Identifier id, double score, long mixed) {}

}
