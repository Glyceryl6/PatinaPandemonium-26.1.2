package dev.patina_pandemonium.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.GeneratedBlockFactory;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.block.VariantFabricatorBlock;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.block.entity.VariantFabricatorBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.effect.TetanusMobEffect;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.item.GeneratedBlockItem;
import dev.patina_pandemonium.menu.VariantFabricatorMenu;
import dev.patina_pandemonium.recipe.VariantFormRecipe;
import dev.patina_pandemonium.recipe.VariantWaxingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Runtime variant data, carrier lookup and transformation services. Deferred-register bootstrap and dynamic source discovery are delegated to
 * {@link VariantRegistryBootstrap} and {@link VariantSourceRegistration}, keeping registration lifecycle code separate from runtime queries.
 */
public class DynamicVariantRegistry {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Class<? extends Block>> PROCESSED_BLOCK_TYPES = List.of(
            SlabBlock.class, StairBlock.class, WallBlock.class,
            FenceBlock.class, FenceGateBlock.class, CarpetBlock.class,
            ButtonBlock.class, PressurePlateBlock.class);
    private static final Map<VariantForm, Class<? extends Block>> FORM_TYPES = Map.of(
        VariantForm.SLAB, SlabBlock.class,
        VariantForm.STAIRS, StairBlock.class,
        VariantForm.WALL, WallBlock.class,
        VariantForm.FENCE, FenceBlock.class,
        VariantForm.FENCE_GATE, FenceGateBlock.class,
        VariantForm.CARPET, CarpetBlock.class,
        VariantForm.BUTTON, ButtonBlock.class,
        VariantForm.PRESSURE_PLATE, PressurePlateBlock.class);

    public static final Identifier VARIANT_ITEM_MODEL = PatinaPandemonium.id("variant_item");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PatinaPandemonium.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PatinaPandemonium.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
        Registries.BLOCK_ENTITY_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(
        Registries.RECIPE_SERIALIZER, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(
        NeoForgeRegistries.ATTACHMENT_TYPES, PatinaPandemonium.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantData>> VARIANT_DATA =
        COMPONENTS.registerComponentType("variant_data", builder -> builder.persistent(VariantData.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemVariantData>> ITEM_VARIANT_DATA =
        COMPONENTS.registerComponentType("item_variant_data", builder -> builder.persistent(ItemVariantData.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CraftingChemistry.Data>> CRAFTING_CHEMISTRY =
        COMPONENTS.registerComponentType("crafting_chemistry", builder -> builder.persistent(CraftingChemistry.CODEC)
            .networkSynchronized(CraftingChemistry.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantProvenance.Data>> PROVENANCE =
        COMPONENTS.registerComponentType("variant_provenance", builder -> builder.persistent(VariantProvenance.CODEC)
            .networkSynchronized(VariantProvenance.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantGenetics.Data>> GENETICS =
        COMPONENTS.registerComponentType("variant_genetics", builder -> builder.persistent(VariantGenetics.CODEC)
            .networkSynchronized(VariantGenetics.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ORIGINAL_MAX_DAMAGE =
        COMPONENTS.registerComponentType("original_max_damage", builder -> builder.persistent(Codec.INT));
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemVariantData>> ENTITY_VARIANT_DATA = ATTACHMENTS.register(
        "entity_variant_data", () -> AttachmentType.builder(ItemVariantData::defaultData)
            .serialize(ItemVariantData.CODEC.fieldOf("variant")).sync(ItemVariantData.STREAM_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CraftingChemistry.Data>> ENTITY_CHEMISTRY = ATTACHMENTS.register(
        "entity_crafting_chemistry", () -> AttachmentType.builder(CraftingChemistry::emptyData)
            .serialize(CraftingChemistry.CODEC.fieldOf("chemistry")).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VariantGenetics.Data>> ENTITY_GENETICS = ATTACHMENTS.register(
        "entity_genetics", () -> AttachmentType.builder(VariantGenetics::defaultData)
            .serialize(VariantGenetics.CODEC.fieldOf("genetics")).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VariantProvenance.Data>> ENTITY_PROVENANCE = ATTACHMENTS.register(
        "entity_provenance", () -> AttachmentType.builder(VariantProvenance::defaultData)
            .serialize(VariantProvenance.CODEC.fieldOf("provenance")).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemVariantData>> ENTITY_FIRE_VARIANT_DATA = ATTACHMENTS.register(
        "entity_fire_variant_data", () -> AttachmentType.builder(ItemVariantData::defaultData).sync(ItemVariantData.STREAM_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VariantData>> BLOCK_ENTITY_VARIANT_DATA = ATTACHMENTS.register(
        "block_entity_variant_data", () -> AttachmentType.builder(() -> VariantData.defaultFor(VariantForm.FULL))
            .serialize(VariantData.CODEC.fieldOf("variant")).sync(VariantData.STREAM_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VariantProvenance.Data>> BLOCK_ENTITY_PROVENANCE = ATTACHMENTS.register(
        "block_entity_provenance", () -> AttachmentType.builder(VariantProvenance::defaultData)
            .serialize(VariantProvenance.CODEC.fieldOf("provenance")).sync(VariantProvenance.STREAM_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CraftingChemistry.Data>> BLOCK_ENTITY_CHEMISTRY = ATTACHMENTS.register(
        "block_entity_crafting_chemistry", () -> AttachmentType.builder(CraftingChemistry::emptyData)
            .serialize(CraftingChemistry.CODEC.fieldOf("chemistry")).build());
    public static final DeferredHolder<MobEffect, TetanusMobEffect> TETANUS = MOB_EFFECTS.register(
        "tetanus", () -> new TetanusMobEffect(0x6F7F61));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VariantWaxingRecipe>> VARIANT_WAXING_RECIPE =
        RECIPE_SERIALIZERS.register("variant_waxing", () -> new RecipeSerializer<>(
            VariantWaxingRecipe.MAP_CODEC, VariantWaxingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VariantFormRecipe>> VARIANT_FORM_RECIPE =
            RECIPE_SERIALIZERS.register("variant_form", () -> new RecipeSerializer<>(
                    VariantFormRecipe.MAP_CODEC, VariantFormRecipe.STREAM_CODEC));

    public static final DeferredBlock<VariantFabricatorBlock> VARIANT_FABRICATOR = BLOCKS.register(
        "variant_fabricator", id -> new VariantFabricatorBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F).sound(SoundType.COPPER)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredItem<BlockItem> VARIANT_FABRICATOR_ITEM = ITEMS.registerItem(
        "variant_fabricator", properties -> new BlockItem(VARIANT_FABRICATOR.get(), properties.useBlockDescriptionPrefix()));

    public static final DeferredBlock<Block> FULL = carrier("virtual_full", VariantForm.FULL);
    public static final DeferredBlock<Block> SLAB = carrier("virtual_slab", VariantForm.SLAB);
    public static final DeferredBlock<Block> STAIRS = carrier("virtual_stairs", VariantForm.STAIRS);
    public static final DeferredBlock<Block> WALL = carrier("virtual_wall", VariantForm.WALL);
    public static final DeferredBlock<Block> FENCE = carrier("virtual_fence", VariantForm.FENCE);
    public static final DeferredBlock<Block> FENCE_GATE = carrier("virtual_fence_gate", VariantForm.FENCE_GATE);
    public static final DeferredBlock<Block> CARPET = carrier("virtual_carpet", VariantForm.CARPET);
    public static final DeferredBlock<Block> BUTTON = carrier("virtual_button", VariantForm.BUTTON);
    public static final DeferredBlock<Block> PRESSURE_PLATE = carrier("virtual_pressure_plate", VariantForm.PRESSURE_PLATE);

    public static final DeferredBlock<Block> TRANSLUCENT_FULL = carrier("virtual_translucent_full", VariantForm.FULL, true);
    public static final DeferredBlock<Block> TRANSLUCENT_SLAB = carrier("virtual_translucent_slab", VariantForm.SLAB, true);
    public static final DeferredBlock<Block> TRANSLUCENT_STAIRS = carrier("virtual_translucent_stairs", VariantForm.STAIRS, true);
    public static final DeferredBlock<Block> TRANSLUCENT_WALL = carrier("virtual_translucent_wall", VariantForm.WALL, true);
    public static final DeferredBlock<Block> TRANSLUCENT_FENCE = carrier("virtual_translucent_fence", VariantForm.FENCE, true);
    public static final DeferredBlock<Block> TRANSLUCENT_FENCE_GATE = carrier("virtual_translucent_fence_gate", VariantForm.FENCE_GATE, true);
    public static final DeferredBlock<Block> TRANSLUCENT_CARPET = carrier("virtual_translucent_carpet", VariantForm.CARPET, true);
    public static final DeferredBlock<Block> TRANSLUCENT_BUTTON = carrier("virtual_translucent_button", VariantForm.BUTTON, true);
    public static final DeferredBlock<Block> TRANSLUCENT_PRESSURE_PLATE = carrier("virtual_translucent_pressure_plate", VariantForm.PRESSURE_PLATE, true);

    public static final DeferredItem<GeneratedBlockItem> FULL_ITEM = carrierItem("virtual_full", FULL, VariantForm.FULL);
    public static final DeferredItem<GeneratedBlockItem> SLAB_ITEM = carrierItem("virtual_slab", SLAB, VariantForm.SLAB);
    public static final DeferredItem<GeneratedBlockItem> STAIRS_ITEM = carrierItem("virtual_stairs", STAIRS, VariantForm.STAIRS);
    public static final DeferredItem<GeneratedBlockItem> WALL_ITEM = carrierItem("virtual_wall", WALL, VariantForm.WALL);
    public static final DeferredItem<GeneratedBlockItem> FENCE_ITEM = carrierItem("virtual_fence", FENCE, VariantForm.FENCE);
    public static final DeferredItem<GeneratedBlockItem> FENCE_GATE_ITEM = carrierItem("virtual_fence_gate", FENCE_GATE, VariantForm.FENCE_GATE);
    public static final DeferredItem<GeneratedBlockItem> CARPET_ITEM = carrierItem("virtual_carpet", CARPET, VariantForm.CARPET);
    public static final DeferredItem<GeneratedBlockItem> BUTTON_ITEM = carrierItem("virtual_button", BUTTON, VariantForm.BUTTON);
    public static final DeferredItem<GeneratedBlockItem> PRESSURE_PLATE_ITEM = carrierItem("virtual_pressure_plate", PRESSURE_PLATE, VariantForm.PRESSURE_PLATE);

    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_FULL_ITEM = carrierItem(
        "virtual_translucent_full", TRANSLUCENT_FULL, VariantForm.FULL);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_SLAB_ITEM = carrierItem(
        "virtual_translucent_slab", TRANSLUCENT_SLAB, VariantForm.SLAB);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_STAIRS_ITEM = carrierItem(
        "virtual_translucent_stairs", TRANSLUCENT_STAIRS, VariantForm.STAIRS);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_WALL_ITEM = carrierItem(
        "virtual_translucent_wall", TRANSLUCENT_WALL, VariantForm.WALL);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_FENCE_ITEM = carrierItem(
        "virtual_translucent_fence", TRANSLUCENT_FENCE, VariantForm.FENCE);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_FENCE_GATE_ITEM = carrierItem(
        "virtual_translucent_fence_gate", TRANSLUCENT_FENCE_GATE, VariantForm.FENCE_GATE);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_CARPET_ITEM = carrierItem(
        "virtual_translucent_carpet", TRANSLUCENT_CARPET, VariantForm.CARPET);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_BUTTON_ITEM = carrierItem(
        "virtual_translucent_button", TRANSLUCENT_BUTTON, VariantForm.BUTTON);
    public static final DeferredItem<GeneratedBlockItem> TRANSLUCENT_PRESSURE_PLATE_ITEM = carrierItem(
        "virtual_translucent_pressure_plate", TRANSLUCENT_PRESSURE_PLATE, VariantForm.PRESSURE_PLATE);

    private static final Map<VariantForm, Supplier<? extends Block>> CARRIERS = carriers();
    private static final Map<VariantForm, Supplier<? extends Block>> TRANSLUCENT_CARRIERS = translucentCarriers();
    private static final Map<VariantForm, Supplier<? extends Item>> CARRIER_ITEMS = carrierItems();
    private static final Map<VariantForm, Supplier<? extends Item>> TRANSLUCENT_CARRIER_ITEMS = translucentCarrierItems();
    static final Map<Identifier, EnumMap<VariantForm, Item>> SOURCE_ITEMS = new LinkedHashMap<>();
    static final Map<Identifier, EnumMap<VariantForm, Block>> SOURCE_BLOCKS = new LinkedHashMap<>();
    static final Map<Identifier, EnumMap<VariantForm, Block>> EXISTING_FORMS = new LinkedHashMap<>();
    static final IdentityHashMap<Item, ExistingFormBinding> EXISTING_FORM_OUTPUTS = new IdentityHashMap<>();
    static final Map<Identifier, Block> DELEGATED_CARRIERS = new LinkedHashMap<>();
    static final Map<Identifier, Item> DELEGATED_ITEMS = new LinkedHashMap<>();
    static final LinkedHashSet<Identifier> FULL_SOURCE_IDS = new LinkedHashSet<>();
    static final LinkedHashSet<Identifier> SPECIAL_SOURCE_IDS = new LinkedHashSet<>();
    static final LinkedHashSet<Identifier> NATIVE_BLOCK_ENTITY_SOURCE_IDS = new LinkedHashSet<>();
    static final IdentityHashMap<Block, Identifier> BLOCK_SOURCES = new IdentityHashMap<>();
    static final IdentityHashMap<Item, Identifier> ITEM_SOURCES = new IdentityHashMap<>();
    static final IdentityHashMap<Item, Identifier> DELEGATED_ITEM_SOURCES = new IdentityHashMap<>();
    static final LinkedHashSet<Item> STANDALONE_VARIANT_ITEMS = new LinkedHashSet<>();
    static final ArrayList<CarrierBinding> SOURCE_BINDINGS = new ArrayList<>();
    static final ArrayList<Block> SOURCE_CARRIERS = new ArrayList<>();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatinaVariantBlockEntity>> VARIANT_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("virtual_variant", () -> new BlockEntityType<>(
            PatinaVariantBlockEntity::new, false, legacyGenerated().toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VariantFabricatorBlockEntity>> VARIANT_FABRICATOR_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("variant_fabricator", () -> new BlockEntityType<>(
            VariantFabricatorBlockEntity::new, false, VARIANT_FABRICATOR.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<VariantFabricatorMenu>> VARIANT_FABRICATOR_MENU = MENUS.register(
        "variant_fabricator", () -> new MenuType<>(VariantFabricatorMenu::new, FeatureFlags.VANILLA_SET));

    static volatile List<Identifier> sourceIds;
    static volatile List<Identifier> specialSourceIds;
    static volatile List<Identifier> itemSourceIds;
    static volatile List<Block> generated;
    static boolean sourceCarriersRegistered;

    public static void register(IEventBus modBus) {
        VariantRegistryBootstrap.register(modBus);
    }

    public static List<Block> generated() {
        List<Block> cached = generated;
        if (cached != null) return cached;
        ArrayList<Block> blocks = new ArrayList<>(legacyGenerated());
        blocks.addAll(SOURCE_CARRIERS);
        return Collections.unmodifiableList(blocks);
    }

    public static List<CarrierBinding> sourceBindings() {
        return Collections.unmodifiableList(SOURCE_BINDINGS);
    }

    public static Set<Item> blockVariantItems() {
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        items.addAll(ITEM_SOURCES.keySet());
        items.addAll(DELEGATED_ITEM_SOURCES.keySet());
        CARRIER_ITEMS.values().forEach(item -> items.add(item.get()));
        TRANSLUCENT_CARRIER_ITEMS.values().forEach(item -> items.add(item.get()));
        return Collections.unmodifiableSet(items);
    }

    public static Set<Item> standaloneVariantItems() {
        return Collections.unmodifiableSet(STANDALONE_VARIANT_ITEMS);
    }

    public static boolean isStandaloneVariantItem(Item item) {
        return STANDALONE_VARIANT_ITEMS.contains(item) || isStandaloneItem(item);
    }

    public static Block carrier(VariantForm form) {
        return carrier(form, false);
    }

    public static Block carrier(VariantForm form, boolean nonOccluding) {
        return (nonOccluding ? TRANSLUCENT_CARRIERS : CARRIERS).get(form).get();
    }

    public static Item carrierItem(VariantForm form) {
        return carrierItem(form, false);
    }

    public static Item carrierItem(VariantForm form, boolean nonOccluding) {
        return (nonOccluding ? TRANSLUCENT_CARRIER_ITEMS : CARRIER_ITEMS).get(form).get();
    }

    public static VariantData data(ItemStack stack, VariantForm form) {
        VariantData data = stack.get(VARIANT_DATA.get());
        Identifier source = ITEM_SOURCES.get(stack.getItem());
        if (source == null) source = DELEGATED_ITEM_SOURCES.get(stack.getItem());
        VariantData normalized = (data == null && source != null
            ? new VariantData(source, OxidationStage.FRESH, false, form, null)
            : data == null ? VariantData.defaultFor(form) : data).normalized(form);
        if (stack.getItem() instanceof GeneratedBlockItem) {
            Component name = generatedBlockName(stack, normalized);
            if (!name.equals(stack.get(DataComponents.ITEM_NAME))) {
                stack.set(DataComponents.ITEM_NAME, name);
            }
        }

        return normalized;
    }

    @Nullable
    public static ItemVariantData itemData(ItemStack stack) {
        ItemVariantData data = stack.get(ITEM_VARIANT_DATA.get());
        if (data == null) return null;
        ItemVariantData normalized = data.normalized(stack.getItem());
        if (!normalized.equals(data)) stack.set(ITEM_VARIANT_DATA.get(), normalized);
        if (!VARIANT_ITEM_MODEL.equals(stack.get(DataComponents.ITEM_MODEL))) {
            stack.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
        }

        Component name = variantItemName(stack, normalized);
        if (!name.equals(stack.get(DataComponents.ITEM_NAME))) stack.set(DataComponents.ITEM_NAME, name);
        applyDurabilityProfile(stack, normalized);
        return normalized;
    }

    @Nullable
    public static ItemVariantData peekItemData(ItemStack stack) {
        ItemVariantData data = stack.get(ITEM_VARIANT_DATA.get());
        return data == null ? null : data.normalized(stack.getItem());
    }

    @Nullable
    public static ItemVariantData variantUseData(ItemStack stack) {
        ItemVariantData itemData = itemData(stack);
        if (itemData != null) return itemData;
        VariantData blockData = stack.get(VARIANT_DATA.get());
        Identifier sourceId = ITEM_SOURCES.get(stack.getItem());
        if (sourceId == null) sourceId = DELEGATED_ITEM_SOURCES.get(stack.getItem());
        if (blockData == null && sourceId == null) return null;
        VariantData normalized = (blockData == null
            ? new VariantData(sourceId, OxidationStage.FRESH, false, VariantForm.FULL, null)
            : blockData).normalized(blockData == null ? VariantForm.FULL : blockData.form());
        Item sourceItem = BuiltInRegistries.BLOCK.getValue(normalized.sourceId()).asItem();
        Identifier itemId = sourceItem == Items.AIR ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : BuiltInRegistries.ITEM.getKey(sourceItem);
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        return new ItemVariantData(itemId, normalized.stage(), normalized.waxed(), normalized.dyeColor(), modelId == null ? itemId : modelId, normalized.customColor());
    }

    public static ItemStack inheritCraftingVariant(CraftingInput input, ItemStack output) {
        return inheritCraftingVariant(input, output, "crafting");
    }

    public static ItemStack inheritCraftingVariant(CraftingInput input, ItemStack output, String operation) {
        ItemStack result = inheritCraftingVariantVisual(input, output);
        return VariantProvenance.craft(input, result, operation);
    }

    private static ItemStack inheritCraftingVariantVisual(CraftingInput input, ItemStack output) {
        if (output.isEmpty()) return output;
        CraftingChemistry.Synthesis synthesis = CraftingChemistry.synthesize(input);
        if (synthesis == null) return output;

        VariantData explicitBlockData = output.get(VARIANT_DATA.get());
        if (explicitBlockData != null) {
            VariantData data = explicitBlockData.normalized(explicitBlockData.form()).withCustomColor(synthesis.customColor());
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), synthesis.data());
        }

        ItemVariantData explicitItemData = output.get(ITEM_VARIANT_DATA.get());
        if (explicitItemData != null) {
            ItemStack result = output.copy();
            result.set(ITEM_VARIANT_DATA.get(), explicitItemData.normalized(output.getItem()).withCustomColor(synthesis.customColor()));
            result.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
            return withCraftingChemistry(result, synthesis.data());
        }

        OxidationStage stage = synthesis.stage();
        boolean waxed = synthesis.waxed();
        DyeColor dye = synthesis.dyeColor();
        Integer customColor = synthesis.customColor();
        ExistingFormBinding existing = EXISTING_FORM_OUTPUTS.get(output.getItem());
        if (existing != null) {
            VariantData data = new VariantData(existing.sourceId(), stage, waxed, existing.form(), dye, customColor);
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), synthesis.data());
        }

        Identifier fullSource = fullSourceId(output);
        if (fullSource != null) {
            VariantData data = new VariantData(fullSource, stage, waxed, VariantForm.FULL, dye, customColor);
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), synthesis.data());
        }

        Identifier specialSource = specialSourceId(output);
        if (specialSource != null) {
            VariantData data = new VariantData(specialSource, stage, waxed, VariantForm.FULL, dye, customColor);
            ItemStack target = NATIVE_BLOCK_ENTITY_SOURCE_IDS.contains(specialSource)
                ? variantItemStack(output.copyWithCount(output.getCount()), stage, waxed, dye, customColor)
                : delegatedStack(output, data, output.getCount());
            return withCraftingChemistry(mergeCraftingOutput(output, target), synthesis.data());
        }

        if (isStandaloneVariantItem(output.getItem())) {
            ItemStack target = variantItemStack(output.copyWithCount(output.getCount()), stage, waxed, dye, customColor);
            return withCraftingChemistry(target, synthesis.data());
        }

        ItemStack target = variantItemStack(output.copyWithCount(output.getCount()), stage, waxed, dye, customColor);
        return withCraftingChemistry(target, synthesis.data());
    }

    public static ItemStack stonecutterRecipeInput(ItemStack input) {
        if (input.isEmpty()) return input;
        VariantData blockData = input.get(VARIANT_DATA.get());
        if (blockData != null) {
            VariantData normalized = blockData.normalized(blockData.form());
            Block source = existingForm(normalized.sourceId(), normalized.form());
            if (source == null || source == Blocks.AIR || source.asItem() == Items.AIR) return input;
            return new ItemStack(source.asItem(), input.getCount());
        }

        if (input.getItem() instanceof GeneratedBlockItem blockItem) {
            Identifier sourceId = ITEM_SOURCES.get(input.getItem());
            Block source = sourceId == null ? null : existingForm(sourceId, blockItem.form());
            if (source != null && source != Blocks.AIR && source.asItem() != Items.AIR) {
                return new ItemStack(source.asItem(), input.getCount());
            }
        }

        Identifier delegatedSource = DELEGATED_ITEM_SOURCES.get(input.getItem());
        if (delegatedSource != null) {
            Block source = BuiltInRegistries.BLOCK.getValue(delegatedSource);
            if (source != Blocks.AIR && source.asItem() != Items.AIR) return new ItemStack(source.asItem(), input.getCount());
        }

        ItemVariantData itemData = peekItemData(input);
        if (itemData == null) return input;
        Item source = BuiltInRegistries.ITEM.getValue(itemData.sourceId());
        return source == Items.AIR ? input : new ItemStack(source, input.getCount());
    }

    public static ItemStack inheritStonecutterVariant(ItemStack input, ItemStack output) {
        return inheritSingleItemVariant(input, output);
    }

    public static ItemStack inheritSingleItemVariant(ItemStack input, ItemStack output) {
        if (input.isEmpty() || output.isEmpty()) return output;
        VariantState state = variantState(input);
        CraftingChemistry.Data chemistry = input.get(CRAFTING_CHEMISTRY.get());
        if ((state == null || !state.transformed()) && chemistry == null) return output;
        OxidationStage stage = state == null ? OxidationStage.FRESH : state.stage();
        boolean waxed = state != null && state.waxed();
        DyeColor dye = state == null ? null : state.dyeColor();
        Integer customColor = state == null ? null : state.customColor();
        if (customColor == null && chemistry != null && chemistry.color() >= 0) customColor = chemistry.color();
        VariantData explicitBlockData = output.get(VARIANT_DATA.get());
        if (explicitBlockData != null) {
            VariantData data = new VariantData(explicitBlockData.sourceId(), stage, waxed, explicitBlockData.form(), dye, customColor);
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), chemistry);
        }

        ItemVariantData explicitItemData = output.get(ITEM_VARIANT_DATA.get());
        if (explicitItemData != null) {
            ItemStack result = output.copy();
            result.set(ITEM_VARIANT_DATA.get(), new ItemVariantData(explicitItemData.sourceId(), stage, waxed, dye, explicitItemData.modelId(), customColor));
            result.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
            return withCraftingChemistry(result, chemistry);
        }

        ExistingFormBinding existing = EXISTING_FORM_OUTPUTS.get(output.getItem());
        if (existing != null) {
            VariantData data = new VariantData(existing.sourceId(), stage, waxed, existing.form(), dye, customColor);
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), chemistry);
        }

        Identifier fullSource = fullSourceId(output);
        if (fullSource != null) {
            VariantData data = new VariantData(fullSource, stage, waxed, VariantForm.FULL, dye, customColor);
            return withCraftingChemistry(mergeCraftingOutput(output, displayStack(data, output.getCount())), chemistry);
        }

        Identifier specialSource = specialSourceId(output);
        if (specialSource != null) {
            VariantData data = new VariantData(specialSource, stage, waxed, VariantForm.FULL, dye, customColor);
            ItemStack target = NATIVE_BLOCK_ENTITY_SOURCE_IDS.contains(specialSource)
                ? variantItemStack(output.copyWithCount(output.getCount()), stage, waxed, dye, customColor)
                : delegatedStack(output, data, output.getCount());
            return withCraftingChemistry(mergeCraftingOutput(output, target), chemistry);
        }

        ItemStack target = variantItemStack(output.copyWithCount(output.getCount()), stage, waxed, dye, customColor);
        return withCraftingChemistry(target, chemistry);
    }

    public static ItemStack stack(VariantData data) {
        return stack(data, 1);
    }

    public static ItemStack displayStack(VariantData data) {
        return displayStack(data, 1);
    }

    public static ItemStack displayStack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        boolean flag = normalized.stage() == OxidationStage.FRESH
                && !normalized.waxed()
                && normalized.dyeColor() == null
                && normalized.customColor() == null;
        if (isNativeBlockEntityVariant(normalized.sourceId(), normalized.form())) {
            Block source = BuiltInRegistries.BLOCK.getValue(normalized.sourceId());
            if (source.asItem() == Items.AIR) return ItemStack.EMPTY;
            if (flag) return new ItemStack(source, Math.max(1, count));
            return variantItemStack(new ItemStack(
                    source, Math.max(1, count)),
                    normalized.stage(),
                    normalized.waxed(),
                    normalized.dyeColor(),
                    normalized.customColor());
        }

        Item specialItem = normalized.form() == VariantForm.FULL ? specialSourceItem(normalized.sourceId()) : null;
        if (specialItem != null) return delegatedStack(new ItemStack(specialItem, Math.max(1, count)), normalized, count);
        if (flag) {
            Block source = normalized.form() == VariantForm.FULL
                    ? BuiltInRegistries.BLOCK.getValue(normalized.sourceId())
                    : existingForm(normalized.sourceId(), normalized.form());
            if (source != null && source != Blocks.AIR && source.asItem() != Items.AIR) {
                return new ItemStack(source, Math.max(1, count));
            }
        }

        return stack(normalized, count);
    }

    public static ItemStack stack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        if (isNativeBlockEntityVariant(normalized.sourceId(), normalized.form())) {
            Block source = BuiltInRegistries.BLOCK.getValue(normalized.sourceId());
            return source.asItem() == Items.AIR ? ItemStack.EMPTY : variantItemStack(new ItemStack(
                    source, Math.max(1, count)), normalized.stage(), normalized.waxed(), normalized.dyeColor(), normalized.customColor());
        }

        Item specialItem = normalized.form() == VariantForm.FULL ? specialSourceItem(normalized.sourceId()) : null;
        if (specialItem != null) return delegatedStack(new ItemStack(specialItem, Math.max(1, count)), normalized, count);
        Item item = sourceItem(normalized.sourceId(), normalized.form());
        if (item == null) item = carrierItem(normalized.form(), requiresNonOccludingCarrier(normalized.sourceId()));
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        stack.set(VARIANT_DATA.get(), normalized);
        stack.set(DataComponents.ITEM_NAME, item.getName(stack));
        return stack;
    }

    public static ItemStack fabricate(ItemStack input, VariantForm form, OxidationStage stage, boolean waxed, @Nullable DyeColor dye, int count) {
        return fabricate(input, form, stage, waxed, dye, null, count);
    }

    public static ItemStack fabricate(ItemStack input, VariantForm form, OxidationStage stage, boolean waxed, @Nullable DyeColor dye, @Nullable Integer customColor, int count) {
        if (input.isEmpty() || !supportsForm(input, form)) return ItemStack.EMPTY;
        ItemStack output;
        Identifier fullSource = fullSourceId(input);
        if (fullSource != null) {
            output = displayStack(new VariantData(fullSource, stage, waxed, form, dye, customColor), count);
        } else {
            Identifier specialSource = specialSourceId(input);
            if (specialSource != null) {
                VariantData data = new VariantData(specialSource, stage, waxed, VariantForm.FULL, dye, customColor);
                if (NATIVE_BLOCK_ENTITY_SOURCE_IDS.contains(specialSource)) {
                    output = variantItemStack(input.copyWithCount(Math.max(1, count)), stage, waxed, dye, customColor);
                } else {
                    output = delegatedStack(input, data, count);
                }
            } else {
                output = variantItemStack(input.copyWithCount(Math.max(1, count)), stage, waxed, dye, customColor);
            }
        }

        if (output.isEmpty()) return output;
        CraftingChemistry.Data chemistry = input.get(CRAFTING_CHEMISTRY.get());
        if (chemistry != null) {
            output.set(CRAFTING_CHEMISTRY.get(), CraftingChemistry.retarget(chemistry, stage, waxed, dye, customColor));
        }
        return output;
    }

    public static ItemStack transform(ItemStack input, OxidationStage stage, boolean waxed, @Nullable DyeColor dye) {
        VariantState state = variantState(input);
        return transform(input, stage, waxed, dye, state == null ? null : state.customColor());
    }

    public static ItemStack transform(ItemStack input, OxidationStage stage, boolean waxed, @Nullable DyeColor dye, @Nullable Integer customColor) {
        if (input.isEmpty() || !supportsFabrication(input)) return ItemStack.EMPTY;
        VariantState state = variantState(input);
        VariantData current = input.get(VARIANT_DATA.get());
        ItemStack target;
        if (current != null) {
            target = stack(new VariantData(current.sourceId(), stage, waxed, current.form(), dye, customColor), input.getCount());
        } else {
            ExistingFormBinding existing = EXISTING_FORM_OUTPUTS.get(input.getItem());
            target = existing == null
                ? fabricate(input, VariantForm.FULL, stage, waxed, dye, input.getCount())
                : displayStack(new VariantData(existing.sourceId(), stage, waxed, existing.form(), dye, customColor), input.getCount());
        }

        if (!target.isEmpty() && customColor != null) {
            VariantData blockData = target.get(VARIANT_DATA.get());
            ItemVariantData itemData = target.get(ITEM_VARIANT_DATA.get());
            if (blockData != null) target.set(VARIANT_DATA.get(), blockData.withCustomColor(customColor));
            if (itemData != null) target.set(ITEM_VARIANT_DATA.get(), itemData.withCustomColor(customColor));
        }

        if (target.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = mergeCraftingOutput(input, target);
        CraftingChemistry.Data chemistry = input.get(CRAFTING_CHEMISTRY.get());
        if (chemistry != null) result.set(CRAFTING_CHEMISTRY.get(), CraftingChemistry.retarget(chemistry, stage, waxed, dye, customColor));
        OxidationStage fromStage = state == null ? null : state.stage();
        return VariantProvenance.localStateEdit(input, result, variantOperation(state, stage, waxed, dye, customColor), fromStage, stage, waxed);
    }

    public static ItemStack waxedCopy(ItemStack input) {
        VariantState state = variantState(input);
        if (state == null || state.waxed() || hasExistingWaxingRecipe(input)) return ItemStack.EMPTY;
        return transform(input, state.stage(), true, state.dyeColor());
    }

    public static ItemStack oxidizedCopy(ItemStack input) {
        VariantState state = variantState(input);
        OxidationStage next = state == null ? null : state.stage().next();
        if (next == null || state.waxed()) return ItemStack.EMPTY;
        return transform(input, next, false, state.dyeColor());
    }

    public static ItemStack cleanOxidationCopy(ItemStack input) {
        VariantState state = variantState(input);
        if (state == null || state.stage() == OxidationStage.FRESH || state.waxed()) return ItemStack.EMPTY;
        ItemVariantData itemData = peekItemData(input);
        if (itemData != null) {
            ItemStack cleaned = input.copy();
            ItemVariantData cleanedData = new ItemVariantData(itemData.sourceId(), OxidationStage.FRESH, false,
                itemData.dyeColor(), itemData.modelId(), itemData.customColor());
            cleaned.set(ITEM_VARIANT_DATA.get(), cleanedData);
            cleaned.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
            cleaned.remove(DataComponents.ITEM_NAME);
            applyDurabilityProfile(cleaned, cleanedData);
            CraftingChemistry.Data chemistry = input.get(CRAFTING_CHEMISTRY.get());
            if (chemistry != null) cleaned.set(CRAFTING_CHEMISTRY.get(), CraftingChemistry.retarget(
                chemistry, OxidationStage.FRESH, false, itemData.dyeColor(), itemData.customColor()));
            return VariantProvenance.localStateEdit(input, cleaned, "lightning_clean", itemData.stage(), OxidationStage.FRESH, false);
        }

        VariantData blockData = input.get(VARIANT_DATA.get());
        if (blockData == null) return ItemStack.EMPTY;
        VariantData cleanedData = new VariantData(blockData.sourceId(), OxidationStage.FRESH, false, blockData.form(),
            blockData.dyeColor(), blockData.customColor());
        ItemStack target = stack(cleanedData, input.getCount());
        if (target.isEmpty()) return ItemStack.EMPTY;
        ItemStack cleaned = mergeCraftingOutput(input, target);
        CraftingChemistry.Data chemistry = input.get(CRAFTING_CHEMISTRY.get());
        if (chemistry != null) cleaned.set(CRAFTING_CHEMISTRY.get(), CraftingChemistry.retarget(
            chemistry, OxidationStage.FRESH, false, blockData.dyeColor(), blockData.customColor()));
        return VariantProvenance.localStateEdit(input, cleaned, "lightning_clean", blockData.stage(), OxidationStage.FRESH, false);
    }

    public static double durabilityMultiplier(OxidationStage stage, boolean waxed) {
        double[] multipliers = waxed ? PatinaRules.INSTANCE.waxedDurabilityMultipliers : PatinaRules.INSTANCE.durabilityMultipliers;
        return multipliers[stage.ordinal()];
    }

    public static boolean supportsFabrication(ItemStack stack) {
        if (stack.isEmpty()) return false;
        VariantData blockData = stack.get(VARIANT_DATA.get());
        if (blockData != null) {
            return BuiltInRegistries.BLOCK.getValue(blockData.sourceId()) != Blocks.AIR;
        }

        if (EXISTING_FORM_OUTPUTS.containsKey(stack.getItem())
                || fullSourceId(stack) != null
                || specialSourceId(stack) != null) return true;
        ItemVariantData data = peekItemData(stack);
        if (data != null) return isStandaloneItemId(data.sourceId());
        return isStandaloneVariantItem(stack.getItem());
    }

    public static boolean supportsForm(ItemStack stack, VariantForm form) {
        VariantData blockData = stack.get(VARIANT_DATA.get());
        if (blockData != null) return blockData.form() == VariantForm.FULL || blockData.form() == form;
        ExistingFormBinding existing = EXISTING_FORM_OUTPUTS.get(stack.getItem());
        if (existing != null) return existing.form() == form;
        return supportsFabrication(stack) && (fullSourceId(stack) != null || form == VariantForm.FULL);
    }

    public static List<Identifier> sourceIds() {
        List<Identifier> cached = sourceIds;
        return cached == null ? discoverFullSources() : cached;
    }

    public static List<Identifier> specialSourceIds() {
        List<Identifier> cached = specialSourceIds;
        return cached == null ? discoverSpecialSources() : cached;
    }

    public static List<Identifier> itemSourceIds() {
        List<Identifier> cached = itemSourceIds;
        return cached == null ? discoverItemSources() : cached;
    }

    public static boolean isSource(Identifier id) {
        return isFullSource(id, BuiltInRegistries.BLOCK.getValue(id), PatinaRules.INSTANCE);
    }

    public static boolean isSource(Identifier id, Block block, PatinaRules rules) {
        return isFullSource(id, block, rules);
    }

    public static boolean isFullSource(Identifier id, Block block, PatinaRules rules) {
        if (!isCommonBlockSource(id, block, rules) || isProcessedBlock(block)) return false;
        BlockState state = block.defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) return false;
        try {
            return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                && Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isSpecialSource(Identifier id, Block block, PatinaRules rules) {
        return isCommonBlockSource(id, block, rules) && !isProcessedBlock(block) && !isFullSource(id, block, rules);
    }

    public static boolean hasExistingForm(Identifier sourceId, VariantForm form) {
        return existingForm(sourceId, form) != null;
    }

    @Nullable
    public static Block existingForm(Identifier sourceId, VariantForm form) {
        if (form == VariantForm.FULL) return BuiltInRegistries.BLOCK.getValue(sourceId);
        EnumMap<VariantForm, Block> forms = EXISTING_FORMS.computeIfAbsent(sourceId, DynamicVariantRegistry::discoverExistingForms);
        return forms.get(form);
    }

    public static boolean isGeneratedId(Identifier id) {
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block instanceof PatinaOxidizable;
    }

    public static boolean isExternal(Identifier id) {
        return !id.getNamespace().equals("minecraft") && !id.getNamespace().equals(PatinaPandemonium.MOD_ID);
    }

    @Nullable
    public static Identifier sourceId(Block block) {
        return BLOCK_SOURCES.get(block);
    }

    @Nullable
    public static Block fullCarrier(Block source) {
        Identifier sourceId = BuiltInRegistries.BLOCK.getKey(source);
        EnumMap<VariantForm, Block> sourceBlocks = SOURCE_BLOCKS.get(sourceId);
        Block carrier = sourceBlocks == null ? null : sourceBlocks.get(VariantForm.FULL);
        return carrier == null ? DELEGATED_CARRIERS.get(sourceId) : carrier;
    }

    public static void replaceSourceBlock(ServerLevel level, BlockPos pos, BlockState sourceState, VariantData data) {
        Identifier sourceId = BuiltInRegistries.BLOCK.getKey(sourceState.getBlock());
        Block carrier = fullCarrier(sourceState.getBlock());
        if (carrier == null) return;
        BlockState targetState = carrier instanceof PatinaDelegatingBlock ? carrier.withPropertiesOf(sourceState) : carrier.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        if (!level.setBlock(pos, targetState, flags)) return;
        if (level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) {
            blockEntity.setData(new VariantData(sourceId, data.stage(), data.waxed(), VariantForm.FULL, data.dyeColor(), data.customColor()));
        }

        level.updateNeighborsAt(pos, carrier);
    }

    public static boolean isDelegatedSource(Block block) {
        return DELEGATED_CARRIERS.containsKey(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static boolean isNativeBlockEntitySource(Block block) {
        return NATIVE_BLOCK_ENTITY_SOURCE_IDS.contains(BuiltInRegistries.BLOCK.getKey(block));
    }

    @Nullable
    public static VariantData blockEntityVariantData(BlockEntity blockEntity) {
        if (blockEntity instanceof PatinaVariantBlockEntity patina) return patina.data();
        return blockEntity.getExistingDataOrNull(BLOCK_ENTITY_VARIANT_DATA.get());
    }

    public static void setBlockEntityVariantData(BlockEntity blockEntity, VariantData data) {
        if (blockEntity instanceof PatinaVariantBlockEntity patina) {
            patina.setData(data);
            return;
        }

        blockEntity.setData(BLOCK_ENTITY_VARIANT_DATA.get(), data.normalized(VariantForm.FULL));
        blockEntity.setChanged();
    }

    public static VariantProvenance.@Nullable Data blockEntityProvenance(BlockEntity blockEntity) {
        return blockEntity.getExistingDataOrNull(BLOCK_ENTITY_PROVENANCE.get());
    }

    public static void setBlockEntityProvenance(BlockEntity blockEntity, VariantProvenance.Data data) {
        blockEntity.setData(BLOCK_ENTITY_PROVENANCE.get(), data);
        blockEntity.setChanged();
    }

    public static CraftingChemistry.@Nullable Data blockEntityChemistry(BlockEntity blockEntity) {
        return blockEntity.getExistingDataOrNull(BLOCK_ENTITY_CHEMISTRY.get());
    }

    public static void setBlockEntityChemistry(BlockEntity blockEntity, CraftingChemistry.Data data) {
        blockEntity.setData(BLOCK_ENTITY_CHEMISTRY.get(), data);
        blockEntity.setChanged();
    }

    private static String variantOperation(@Nullable VariantState previous, OxidationStage stage, boolean waxed, @Nullable DyeColor dye,
                                           @Nullable Integer customColor) {
        if (previous == null) return "variant_transform";
        if (previous.waxed() != waxed) return waxed ? "wax" : "unwax";
        if (previous.stage() != stage) return stage.ordinal() > previous.stage().ordinal() ? "oxidize" : "clean_oxidation";
        if (!Objects.equals(previous.dyeColor(), dye) || !Objects.equals(previous.customColor(), customColor)) return "recolor";
        return "variant_transform";
    }

    static List<Identifier> discoverFullSources() {
        ArrayList<Identifier> discovered = new ArrayList<>();
        PatinaRules rules = PatinaRules.INSTANCE;
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            if (isFullSource(entry.getKey().identifier(), entry.getValue(), rules)) {
                discovered.add(entry.getKey().identifier());
            }
        });

        discovered.sort(Identifier::compareTo);
        return Collections.unmodifiableList(discovered);
    }

    static List<Identifier> discoverSpecialSources() {
        ArrayList<Identifier> discovered = new ArrayList<>();
        PatinaRules rules = PatinaRules.INSTANCE;
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            if (isSpecialSource(entry.getKey().identifier(), entry.getValue(), rules)) {
                discovered.add(entry.getKey().identifier());
            }
        });

        discovered.sort(Identifier::compareTo);
        return Collections.unmodifiableList(discovered);
    }

    static List<Identifier> discoverItemSources() {
        ArrayList<Identifier> discovered = new ArrayList<>();
        PatinaRules rules = PatinaRules.INSTANCE;
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            Identifier id = entry.getKey().identifier();
            Item item = entry.getValue();
            if (item != Items.AIR && !(item instanceof BlockItem) && rules.namespaceAllowed(id.getNamespace())
                && (rules.excludedItems == null || !rules.excludedItems.contains(id.toString()))) discovered.add(id);
        });
        discovered.sort(Identifier::compareTo);
        return Collections.unmodifiableList(discovered);
    }

    static EnumMap<VariantForm, Block> discoverExistingForms(Identifier sourceId) {
        EnumMap<VariantForm, Block> forms = new EnumMap<>(VariantForm.class);
        for (Map.Entry<VariantForm, Class<? extends Block>> entry : FORM_TYPES.entrySet()) {
            Block block = findExistingForm(sourceId, entry.getKey(), entry.getValue());
            if (block != null) forms.put(entry.getKey(), block);
        }
        return forms;
    }

    @Nullable
    private static Block findExistingForm(Identifier sourceId, VariantForm form, Class<? extends Block> expectedType) {
        Block configured = configuredExistingForm(sourceId, form, expectedType);
        if (configured != null) return configured;
        LinkedHashSet<String> stems = new LinkedHashSet<>();
        String sourcePath = sourceId.getPath();
        stems.add(sourcePath);
        if (sourcePath.endsWith("_planks")) {
            stems.add(sourcePath.substring(0, sourcePath.length() - "_planks".length()));
        }

        if (sourcePath.endsWith("_block")) {
            String stem = sourcePath.substring(0, sourcePath.length() - "_block".length());
            Identifier planksId = Identifier.fromNamespaceAndPath(sourceId.getNamespace(), stem + "_planks");
            Block planks = BuiltInRegistries.BLOCK.getValue(planksId);
            if (!isFullSource(planksId, planks, PatinaRules.INSTANCE)) stems.add(stem);
        }

        if (sourcePath.endsWith("s")) {
            stems.add(sourcePath.substring(0, sourcePath.length() - 1));
        }

        for (String stem : List.copyOf(stems)) {
            Identifier candidateId = Identifier.fromNamespaceAndPath(sourceId.getNamespace(), stem + "_" + form.id());
            Block candidate = BuiltInRegistries.BLOCK.getValue(candidateId);
            if (candidate != Blocks.AIR && expectedType.isInstance(candidate)) return candidate;
        }

        return null;
    }

    @Nullable
    private static Block configuredExistingForm(Identifier sourceId, VariantForm form, Class<? extends Block> expectedType) {
        JsonObject overrides = PatinaRules.INSTANCE.existingFormOverrides;
        JsonElement sourceOverride = overrides == null ? null : overrides.get(sourceId.toString());
        if (sourceOverride == null || !sourceOverride.isJsonObject()) return null;
        JsonElement formOverride = sourceOverride.getAsJsonObject().get(form.id());
        if (formOverride == null || !formOverride.isJsonPrimitive()) return null;
        Identifier candidateId = Identifier.tryParse(formOverride.getAsString());
        if (candidateId == null) return null;
        Block candidate = BuiltInRegistries.BLOCK.getValue(candidateId);
        return candidate != Blocks.AIR && expectedType.isInstance(candidate) ? candidate : null;
    }

    private static boolean isCommonBlockSource(Identifier id, Block block, PatinaRules rules) {
        return rules.namespaceAllowed(id.getNamespace())
            && (rules.excludedBlocks == null || !rules.excludedBlocks.contains(id.toString()))
            && block != null && block != Blocks.AIR
            && !(block instanceof PatinaOxidizable)
            && !(block instanceof VariantFabricatorBlock);
    }

    private static boolean isProcessedBlock(Block block) {
        return PROCESSED_BLOCK_TYPES.stream().anyMatch(type -> type.isInstance(block));
    }

    @Nullable
    public static Identifier fullSourceId(ItemStack stack) {
        Identifier registered = ITEM_SOURCES.get(stack.getItem());
        if (registered != null) return registered;
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) return null;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return FULL_SOURCE_IDS.contains(id) ? id : null;
    }

    @Nullable
    public static Identifier specialSourceId(ItemStack stack) {
        Identifier registered = DELEGATED_ITEM_SOURCES.get(stack.getItem());
        if (registered != null) return registered;
        ItemVariantData legacyData = stack.get(ITEM_VARIANT_DATA.get());
        Item sourceItem = legacyData == null ? stack.getItem() : BuiltInRegistries.ITEM.getValue(legacyData.sourceId());
        Block source = Block.byItem(sourceItem);
        if (source == Blocks.AIR) return null;
        Identifier sourceId = BuiltInRegistries.BLOCK.getKey(source);
        return SPECIAL_SOURCE_IDS.contains(sourceId) ? sourceId : null;
    }

    private static boolean isStandaloneItemId(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item != Items.AIR && isStandaloneVariantItem(item);
    }

    private static boolean isStandaloneItem(Item item) {
        if (item == Items.AIR || item instanceof BlockItem) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        PatinaRules rules = PatinaRules.INSTANCE;
        return rules.namespaceAllowed(id.getNamespace()) && (rules.excludedItems == null || !rules.excludedItems.contains(id.toString()));
    }

    @Nullable
    private static VariantState variantState(ItemStack stack) {
        VariantData blockData = stack.get(VARIANT_DATA.get());
        if (blockData != null) {
            VariantData normalized = blockData.normalized(blockData.form());
            return new VariantState(normalized.stage(), normalized.waxed(), normalized.dyeColor(), normalized.customColor(), true);
        }

        ItemVariantData itemData = peekItemData(stack);
        if (itemData != null) return new VariantState(itemData.stage(), itemData.waxed(), itemData.dyeColor(), itemData.customColor(), true);
        if (ITEM_SOURCES.containsKey(stack.getItem()) || DELEGATED_ITEM_SOURCES.containsKey(stack.getItem())) {
            return new VariantState(OxidationStage.FRESH, false, null, null, true);
        }

        if (EXISTING_FORM_OUTPUTS.containsKey(stack.getItem()) || fullSourceId(stack) != null
            || specialSourceId(stack) != null || STANDALONE_VARIANT_ITEMS.contains(stack.getItem())) {
            return new VariantState(OxidationStage.FRESH, false, null, null, false);
        }

        return null;
    }

    private static ItemStack withCraftingChemistry(ItemStack stack, CraftingChemistry.@Nullable Data data) {
        if (!stack.isEmpty() && data != null) stack.set(CRAFTING_CHEMISTRY.get(), data);
        return stack;
    }

    private static ItemStack mergeCraftingOutput(ItemStack output, ItemStack target) {
        if (target.isEmpty()) return output;
        ItemStack result = output.transmuteCopy(target.getItem(), output.getCount());
        result.remove(VARIANT_DATA.get());
        result.remove(ITEM_VARIANT_DATA.get());
        VariantData blockData = target.get(VARIANT_DATA.get());
        ItemVariantData itemData = target.get(ITEM_VARIANT_DATA.get());
        if (blockData != null) result.set(VARIANT_DATA.get(), blockData);
        if (itemData != null) result.set(ITEM_VARIANT_DATA.get(), itemData);
        Integer originalMaxDamage = target.get(ORIGINAL_MAX_DAMAGE.get());
        Integer maxDamage = target.get(DataComponents.MAX_DAMAGE);
        Integer damage = target.get(DataComponents.DAMAGE);
        if (originalMaxDamage != null) result.set(ORIGINAL_MAX_DAMAGE.get(), originalMaxDamage);
        else result.remove(ORIGINAL_MAX_DAMAGE.get());
        if (maxDamage != null) result.set(DataComponents.MAX_DAMAGE, maxDamage);
        if (damage != null) result.set(DataComponents.DAMAGE, damage);
        Identifier modelId = target.get(DataComponents.ITEM_MODEL);
        Component name = target.get(DataComponents.ITEM_NAME);
        if (modelId != null) result.set(DataComponents.ITEM_MODEL, modelId);
        if (name != null) result.set(DataComponents.ITEM_NAME, name);
        return result;
    }

    private static ItemStack delegatedStack(ItemStack input, VariantData data, int count) {
        Item target = DELEGATED_ITEMS.get(data.sourceId());
        if (target == null) return ItemStack.EMPTY;
        ItemStack stack = input.transmuteCopy(target, Math.max(1, count));
        stack.remove(ITEM_VARIANT_DATA.get());
        stack.set(VARIANT_DATA.get(), data.normalized(VariantForm.FULL));
        Identifier targetId = BuiltInRegistries.ITEM.getKey(target);
        stack.set(DataComponents.ITEM_MODEL, targetId);
        stack.set(DataComponents.ITEM_NAME, target.getName(stack));
        return stack;
    }

    private static ItemStack variantItemStack(ItemStack stack, OxidationStage stage, boolean waxed, @Nullable DyeColor dye) {
        return variantItemStack(stack, stage, waxed, dye, null);
    }

    private static ItemStack variantItemStack(ItemStack stack, OxidationStage stage, boolean waxed, @Nullable DyeColor dye, @Nullable Integer customColor) {
        ItemVariantData existing = peekItemData(stack);
        Identifier sourceId = existing == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : existing.sourceId();
        Identifier modelId = existing == null ? stack.get(DataComponents.ITEM_MODEL) : existing.modelId();
        if (modelId == null || VARIANT_ITEM_MODEL.equals(modelId)) modelId = sourceId;
        ItemVariantData data = new ItemVariantData(sourceId, stage, waxed, dye, modelId, customColor);
        stack.set(ITEM_VARIANT_DATA.get(), data);
        stack.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
        stack.remove(DataComponents.ITEM_NAME);
        applyDurabilityProfile(stack, data);
        return stack;
    }

    private static void applyDurabilityProfile(ItemStack stack, ItemVariantData data) {
        if (!stack.has(DataComponents.MAX_DAMAGE)) return;
        int currentMax = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (currentMax <= 0) return;
        int baseMax = stack.getOrDefault(ORIGINAL_MAX_DAMAGE.get(), 0);
        double multiplier = durabilityMultiplier(data.stage(), data.waxed());
        if (baseMax <= 0) {
            baseMax = currentMax;
            if (multiplier < 1.0D) {
                stack.set(ORIGINAL_MAX_DAMAGE.get(), baseMax);
            }
        }

        int targetMax = Math.max(1, (int) Math.floor(baseMax * multiplier));
        if (targetMax == currentMax) return;
        int currentDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        double remainingRatio = (double) Math.max(0, currentMax - currentDamage) / currentMax;
        int targetDamage = Math.clamp(targetMax - (int) Math.round(targetMax * remainingRatio), 0, targetMax - 1);
        if (targetMax < currentMax) stack.set(DataComponents.DAMAGE, targetDamage);
        stack.set(DataComponents.MAX_DAMAGE, targetMax);
        if (targetMax >= currentMax) stack.set(DataComponents.DAMAGE, targetDamage);
    }

    private static void restoreDurability(ItemStack cleaned, ItemStack source) {
        int baseMax = source.getOrDefault(ORIGINAL_MAX_DAMAGE.get(), 0);
        if (baseMax <= 0 || !cleaned.has(DataComponents.MAX_DAMAGE)) return;
        int currentMax = Math.max(1, source.getOrDefault(DataComponents.MAX_DAMAGE, baseMax));
        int currentDamage = source.getOrDefault(DataComponents.DAMAGE, 0);
        double remainingRatio = (double) Math.max(0, currentMax - currentDamage) / currentMax;
        cleaned.set(DataComponents.MAX_DAMAGE, baseMax);
        cleaned.set(DataComponents.DAMAGE, Math.clamp(baseMax - (int) Math.round(baseMax * remainingRatio), 0, baseMax - 1));
        cleaned.remove(ORIGINAL_MAX_DAMAGE.get());
    }

    private static boolean hasExistingWaxingRecipe(ItemStack stack) {
        VariantData blockData = stack.get(VARIANT_DATA.get());
        if (blockData != null) {
            Block source = blockData.form() == VariantForm.FULL
                    ? BuiltInRegistries.BLOCK.getValue(blockData.sourceId())
                    : existingForm(blockData.sourceId(), blockData.form());
            return source != null && source != Blocks.AIR && HoneycombItem.getWaxed(source.defaultBlockState()).isPresent();
        }

        ItemVariantData itemData = peekItemData(stack);
        Item sourceItem = itemData == null ? stack.getItem() : BuiltInRegistries.ITEM.getValue(itemData.sourceId());
        Block source = Block.byItem(sourceItem);
        return source != Blocks.AIR && HoneycombItem.getWaxed(source.defaultBlockState()).isPresent();
    }


    public static ItemStack entityStack(Entity entity, boolean initializeHistory) {
        ItemStack picked = entity.getPickResult();
        if (picked == null || picked.isEmpty()) return ItemStack.EMPTY;
        ItemVariantData data = entity.getExistingDataOrNull(ENTITY_VARIANT_DATA.get());
        ItemStack result = data == null ? picked.copy() : transform(picked, data.stage(), data.waxed(), data.dyeColor(), data.customColor());
        if (result.isEmpty()) result = picked.copy();
        CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(ENTITY_CHEMISTRY.get());
        if (chemistry != null) {
            result.set(CRAFTING_CHEMISTRY.get(), data == null ? chemistry
                : CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        }
        VariantGenetics.Data genetics = entity.getExistingDataOrNull(ENTITY_GENETICS.get());
        if (genetics == null && initializeHistory) genetics = VariantGenetics.initialize(entity);
        if (genetics != null) result.set(GENETICS.get(), genetics);
        VariantProvenance.Data provenance = entity.getExistingDataOrNull(ENTITY_PROVENANCE.get());
        if (provenance == null && initializeHistory) {
            provenance = VariantProvenance.entitySource(entity);
            entity.setData(ENTITY_PROVENANCE.get(), provenance);
        }
        if (provenance != null) result.set(PROVENANCE.get(), provenance);
        return result;
    }

    public static Component variantItemName(ItemStack stack, ItemVariantData data) {
        Item source = BuiltInRegistries.ITEM.getValue(data.sourceId());
        if (source == Items.AIR) source = stack.getItem();
        ItemStack defaultStack = source.getDefaultInstance();
        ItemStack namingStack = stack.transmuteCopy(source, 1);
        namingStack.remove(ITEM_VARIANT_DATA.get());
        namingStack.remove(VARIANT_DATA.get());
        Component defaultName = defaultStack.get(DataComponents.ITEM_NAME);
        namingStack.set(DataComponents.ITEM_NAME,
            defaultName == null ? Component.translatable(source.getDescriptionId()) : defaultName);
        namingStack.set(DataComponents.ITEM_MODEL, data.modelId() == null ? data.sourceId() : data.modelId());
        Component sourceName = source.getName(namingStack);
        if (sourceName.getString().isBlank()) sourceName = Component.translatable(source.getDescriptionId());
        return variantName(data.stageKey(), colorName(data.dyeColor(), data.customColor()), sourceName, Component.empty());
    }

    public static Component variantName(ItemVariantData data, Component sourceName) {
        return variantName(data.stageKey(), colorName(data.dyeColor(), data.customColor()), sourceName, Component.empty());
    }

    public static Component generatedBlockName(ItemStack stack, VariantData data) {
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = Blocks.STONE;
        Component sourceName = DELEGATED_ITEM_SOURCES.containsKey(stack.getItem()) && source.asItem() != Items.AIR
            ? source.asItem().getName(source.asItem().getDefaultInstance()) : source.getName();
        return variantName(data, sourceName);
    }

    public static Component variantName(VariantData data, Component sourceName) {
        return variantName(data.stageKey(), colorName(data.dyeColor(), data.customColor()), sourceName, Component.translatable(data.formKey()));
    }

    private static Component variantName(String stageKey, Component colorName, Component sourceName, Component formName) {
        return Component.translatable("item.patina_pandemonium.variant_name", Component.translatable(stageKey), colorName, sourceName, formName);
    }

    private static Component colorName(@Nullable DyeColor dye, @Nullable Integer customColor) {
        if (customColor != null) return Component.translatable("patina_pandemonium.dye.custom", String.format("#%06X", customColor & 0xFFFFFF));
        return Component.translatable(dye == null ? "patina_pandemonium.dye.none" : "patina_pandemonium.dye." + dye.getSerializedName());
    }

    @Nullable
    private static Item specialSourceItem(Identifier sourceId) {
        return SPECIAL_SOURCE_IDS.contains(sourceId) ? DELEGATED_ITEMS.get(sourceId) : null;
    }

    private static boolean isNativeBlockEntityVariant(Identifier sourceId, VariantForm form) {
        return form == VariantForm.FULL && NATIVE_BLOCK_ENTITY_SOURCE_IDS.contains(sourceId);
    }

    @Nullable
    private static Item sourceItem(Identifier sourceId, VariantForm form) {
        EnumMap<VariantForm, Item> items = SOURCE_ITEMS.get(sourceId);
        return items == null ? null : items.get(form);
    }

    static Identifier sourceCarrierId(Identifier sourceId, VariantForm form) {
        return PatinaPandemonium.id("source/" + sourceId.getNamespace() + "/" + sourceId.getPath() + "/" + form.id());
    }

    static Identifier delegatedCarrierId(Identifier sourceId) {
        return PatinaPandemonium.id("delegated/" + sourceId.getNamespace() + "/" + sourceId.getPath());
    }

    private static DeferredBlock<Block> carrier(String name, VariantForm form) {
        return carrier(name, form, false);
    }

    private static DeferredBlock<Block> carrier(String name, VariantForm form, boolean nonOccluding) {
        return BLOCKS.register(name, id -> GeneratedBlockFactory.create(id, form, nonOccluding));
    }

    private static DeferredItem<GeneratedBlockItem> carrierItem(String name, Supplier<? extends Block> block, VariantForm form) {
        return ITEMS.registerItem(name, properties -> new GeneratedBlockItem(block.get(), form, properties), Item.Properties::useBlockDescriptionPrefix);
    }

    static List<Block> legacyGenerated() {
        return List.of(FULL.get(), SLAB.get(), STAIRS.get(), WALL.get(), FENCE.get(), FENCE_GATE.get(), CARPET.get(), BUTTON.get(), PRESSURE_PLATE.get(),
                TRANSLUCENT_FULL.get(), TRANSLUCENT_SLAB.get(), TRANSLUCENT_STAIRS.get(), TRANSLUCENT_WALL.get(), TRANSLUCENT_FENCE.get(),
                TRANSLUCENT_FENCE_GATE.get(), TRANSLUCENT_CARPET.get(), TRANSLUCENT_BUTTON.get(), TRANSLUCENT_PRESSURE_PLATE.get());
    }

    private static Map<VariantForm, Supplier<? extends Block>> carriers() {
        EnumMap<VariantForm, Supplier<? extends Block>> blocks = new EnumMap<>(VariantForm.class);
        blocks.put(VariantForm.FULL, FULL);
        blocks.put(VariantForm.SLAB, SLAB);
        blocks.put(VariantForm.STAIRS, STAIRS);
        blocks.put(VariantForm.WALL, WALL);
        blocks.put(VariantForm.FENCE, FENCE);
        blocks.put(VariantForm.FENCE_GATE, FENCE_GATE);
        blocks.put(VariantForm.CARPET, CARPET);
        blocks.put(VariantForm.BUTTON, BUTTON);
        blocks.put(VariantForm.PRESSURE_PLATE, PRESSURE_PLATE);
        return Map.copyOf(blocks);
    }

    private static Map<VariantForm, Supplier<? extends Block>> translucentCarriers() {
        EnumMap<VariantForm, Supplier<? extends Block>> blocks = new EnumMap<>(VariantForm.class);
        blocks.put(VariantForm.FULL, TRANSLUCENT_FULL);
        blocks.put(VariantForm.SLAB, TRANSLUCENT_SLAB);
        blocks.put(VariantForm.STAIRS, TRANSLUCENT_STAIRS);
        blocks.put(VariantForm.WALL, TRANSLUCENT_WALL);
        blocks.put(VariantForm.FENCE, TRANSLUCENT_FENCE);
        blocks.put(VariantForm.FENCE_GATE, TRANSLUCENT_FENCE_GATE);
        blocks.put(VariantForm.CARPET, TRANSLUCENT_CARPET);
        blocks.put(VariantForm.BUTTON, TRANSLUCENT_BUTTON);
        blocks.put(VariantForm.PRESSURE_PLATE, TRANSLUCENT_PRESSURE_PLATE);
        return Map.copyOf(blocks);
    }

    private static Map<VariantForm, Supplier<? extends Item>> carrierItems() {
        EnumMap<VariantForm, Supplier<? extends Item>> items = new EnumMap<>(VariantForm.class);
        items.put(VariantForm.FULL, FULL_ITEM);
        items.put(VariantForm.SLAB, SLAB_ITEM);
        items.put(VariantForm.STAIRS, STAIRS_ITEM);
        items.put(VariantForm.WALL, WALL_ITEM);
        items.put(VariantForm.FENCE, FENCE_ITEM);
        items.put(VariantForm.FENCE_GATE, FENCE_GATE_ITEM);
        items.put(VariantForm.CARPET, CARPET_ITEM);
        items.put(VariantForm.BUTTON, BUTTON_ITEM);
        items.put(VariantForm.PRESSURE_PLATE, PRESSURE_PLATE_ITEM);
        return Map.copyOf(items);
    }

    private static Map<VariantForm, Supplier<? extends Item>> translucentCarrierItems() {
        EnumMap<VariantForm, Supplier<? extends Item>> items = new EnumMap<>(VariantForm.class);
        items.put(VariantForm.FULL, TRANSLUCENT_FULL_ITEM);
        items.put(VariantForm.SLAB, TRANSLUCENT_SLAB_ITEM);
        items.put(VariantForm.STAIRS, TRANSLUCENT_STAIRS_ITEM);
        items.put(VariantForm.WALL, TRANSLUCENT_WALL_ITEM);
        items.put(VariantForm.FENCE, TRANSLUCENT_FENCE_ITEM);
        items.put(VariantForm.FENCE_GATE, TRANSLUCENT_FENCE_GATE_ITEM);
        items.put(VariantForm.CARPET, TRANSLUCENT_CARPET_ITEM);
        items.put(VariantForm.BUTTON, TRANSLUCENT_BUTTON_ITEM);
        items.put(VariantForm.PRESSURE_PLATE, TRANSLUCENT_PRESSURE_PLATE_ITEM);
        return Map.copyOf(items);
    }

    private static boolean requiresNonOccludingCarrier(Identifier sourceId) {
        Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
        return source != Blocks.AIR && !source.defaultBlockState().canOcclude();
    }

    record ExistingFormBinding(Identifier sourceId, VariantForm form) {}

    private record VariantState(OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor, @Nullable Integer customColor, boolean transformed) {}

    public record CarrierBinding(Identifier sourceId, VariantForm form, Block block, @Nullable Item item) {}

}
