package dev.patinapandemonium.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.block.VariantFabricatorBlock;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.block.entity.VariantFabricatorBlockEntity;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import dev.patinapandemonium.menu.VariantFabricatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Full blocks keep source-bound form carriers. Non-full blocks use one state-copying delegated carrier per source, while item-only variants stay on the
 * original stack through a data component. This keeps registry growth linear in source blocks rather than in oxidation, wax and dye combinations.
 */
public class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Class<? extends Block>> PROCESSED_BLOCK_TYPES = List.of(
        SlabBlock.class, StairBlock.class, WallBlock.class, FenceBlock.class, FenceGateBlock.class, CarpetBlock.class,
        ButtonBlock.class, PressurePlateBlock.class);
    private static final List<Class<? extends Block>> PROPAGATION_BLOCK_TYPES = List.of(BaseFireBlock.class);
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantData>> VARIANT_DATA =
        COMPONENTS.registerComponentType("variant_data", builder -> builder.persistent(VariantData.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemVariantData>> ITEM_VARIANT_DATA =
        COMPONENTS.registerComponentType("item_variant_data", builder -> builder.persistent(ItemVariantData.CODEC));

    public static final DeferredBlock<VariantFabricatorBlock> VARIANT_FABRICATOR = BLOCKS.register(
        "variant_fabricator",
        id -> new VariantFabricatorBlock(BlockBehaviour.Properties.of()
            .strength(3.5F, 6.0F)
            .sound(SoundType.COPPER)
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
    public static final DeferredBlock<Block> TRANSLUCENT_FENCE_GATE = carrier(
        "virtual_translucent_fence_gate", VariantForm.FENCE_GATE, true);
    public static final DeferredBlock<Block> TRANSLUCENT_CARPET = carrier("virtual_translucent_carpet", VariantForm.CARPET, true);
    public static final DeferredBlock<Block> TRANSLUCENT_BUTTON = carrier("virtual_translucent_button", VariantForm.BUTTON, true);
    public static final DeferredBlock<Block> TRANSLUCENT_PRESSURE_PLATE = carrier(
        "virtual_translucent_pressure_plate", VariantForm.PRESSURE_PLATE, true);

    public static final DeferredItem<GeneratedBlockItem> FULL_ITEM = carrierItem("virtual_full", FULL, VariantForm.FULL);
    public static final DeferredItem<GeneratedBlockItem> SLAB_ITEM = carrierItem("virtual_slab", SLAB, VariantForm.SLAB);
    public static final DeferredItem<GeneratedBlockItem> STAIRS_ITEM = carrierItem("virtual_stairs", STAIRS, VariantForm.STAIRS);
    public static final DeferredItem<GeneratedBlockItem> WALL_ITEM = carrierItem("virtual_wall", WALL, VariantForm.WALL);
    public static final DeferredItem<GeneratedBlockItem> FENCE_ITEM = carrierItem("virtual_fence", FENCE, VariantForm.FENCE);
    public static final DeferredItem<GeneratedBlockItem> FENCE_GATE_ITEM = carrierItem(
        "virtual_fence_gate", FENCE_GATE, VariantForm.FENCE_GATE);
    public static final DeferredItem<GeneratedBlockItem> CARPET_ITEM = carrierItem("virtual_carpet", CARPET, VariantForm.CARPET);
    public static final DeferredItem<GeneratedBlockItem> BUTTON_ITEM = carrierItem("virtual_button", BUTTON, VariantForm.BUTTON);
    public static final DeferredItem<GeneratedBlockItem> PRESSURE_PLATE_ITEM = carrierItem(
        "virtual_pressure_plate", PRESSURE_PLATE, VariantForm.PRESSURE_PLATE);

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
    private static final Map<Identifier, EnumMap<VariantForm, Item>> SOURCE_ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, EnumMap<VariantForm, Block>> EXISTING_FORMS = new LinkedHashMap<>();
    private static final Map<Identifier, Block> DELEGATED_CARRIERS = new LinkedHashMap<>();
    private static final Map<Identifier, Item> DELEGATED_ITEMS = new LinkedHashMap<>();
    private static final LinkedHashSet<Identifier> FULL_SOURCE_IDS = new LinkedHashSet<>();
    private static final LinkedHashSet<Identifier> SPECIAL_SOURCE_IDS = new LinkedHashSet<>();
    private static final IdentityHashMap<Block, Identifier> BLOCK_SOURCES = new IdentityHashMap<>();
    private static final IdentityHashMap<Item, Identifier> ITEM_SOURCES = new IdentityHashMap<>();
    private static final IdentityHashMap<Item, Identifier> DELEGATED_ITEM_SOURCES = new IdentityHashMap<>();
    private static final LinkedHashSet<Item> STANDALONE_VARIANT_ITEMS = new LinkedHashSet<>();
    private static final ArrayList<CarrierBinding> SOURCE_BINDINGS = new ArrayList<>();
    private static final ArrayList<Block> SOURCE_CARRIERS = new ArrayList<>();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatinaVariantBlockEntity>> VARIANT_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("virtual_variant", () -> new BlockEntityType<>(
            PatinaVariantBlockEntity::new, false, legacyGenerated().toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VariantFabricatorBlockEntity>> VARIANT_FABRICATOR_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("variant_fabricator", () -> new BlockEntityType<>(
            VariantFabricatorBlockEntity::new, false, VARIANT_FABRICATOR.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<VariantFabricatorMenu>> VARIANT_FABRICATOR_MENU = MENUS.register(
        "variant_fabricator", () -> new MenuType<>(VariantFabricatorMenu::new, FeatureFlags.VANILLA_SET));

    private static volatile List<Identifier> sourceIds;
    private static volatile List<Identifier> specialSourceIds;
    private static volatile List<Identifier> itemSourceIds;
    private static volatile List<Block> generated;
    private static boolean sourceCarriersRegistered;

    public static void register(IEventBus modBus) {
        modBus.addListener(DynamicVariantRegistry::onRegister);
        modBus.addListener(DynamicVariantRegistry::onBlockEntityTypeAddBlocks);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
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

    public static boolean isStandaloneVariantItem(Item item) {
        return STANDALONE_VARIANT_ITEMS.contains(item);
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
            if (!name.equals(stack.get(DataComponents.ITEM_NAME))) stack.set(DataComponents.ITEM_NAME, name);
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
        return normalized;
    }

    public static ItemStack stack(VariantData data) {
        return stack(data, 1);
    }

    public static ItemStack displayStack(VariantData data) {
        return displayStack(data, 1);
    }

    public static ItemStack displayStack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        Item specialItem = normalized.form() == VariantForm.FULL ? specialSourceItem(normalized.sourceId()) : null;
        if (specialItem != null) return delegatedStack(new ItemStack(specialItem, Math.max(1, count)), normalized, count);
        if (normalized.stage() == OxidationStage.FRESH && !normalized.waxed() && normalized.dyeColor() == null) {
            Block source = normalized.form() == VariantForm.FULL
                ? BuiltInRegistries.BLOCK.getValue(normalized.sourceId()) : existingForm(normalized.sourceId(), normalized.form());
            if (source != null && source != Blocks.AIR && source.asItem() != Items.AIR) return new ItemStack(source, Math.max(1, count));
        }
        return stack(normalized, count);
    }

    public static ItemStack stack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
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
        if (input.isEmpty() || !supportsForm(input, form)) return ItemStack.EMPTY;
        Identifier fullSource = fullSourceId(input);
        if (fullSource != null) return displayStack(new VariantData(fullSource, stage, waxed, form, dye), count);
        Identifier specialSource = specialSourceId(input);
        if (specialSource != null) {
            return delegatedStack(input, new VariantData(specialSource, stage, waxed, VariantForm.FULL, dye), count);
        }
        return variantItemStack(input.copyWithCount(Math.max(1, count)), stage, waxed, dye);
    }

    public static boolean supportsFabrication(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (fullSourceId(stack) != null || specialSourceId(stack) != null) return true;
        ItemVariantData data = itemData(stack);
        if (data != null) return isStandaloneItemId(data.sourceId());
        return STANDALONE_VARIANT_ITEMS.contains(stack.getItem());
    }

    public static boolean supportsForm(ItemStack stack, VariantForm form) {
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
        if (!isCommonBlockSource(id, block, rules) || block instanceof EntityBlock || isProcessedBlock(block)) return false;
        if (block.asItem() == Items.AIR || block.getStateDefinition().getPossibleStates().size() > rules.maximumDelegatedBlockStates) return false;
        return !isFullSource(id, block, rules);
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
    public static Block delegatedCarrier(Block source) {
        Identifier sourceId = BuiltInRegistries.BLOCK.getKey(source);
        return DELEGATED_CARRIERS.get(sourceId);
    }

    public static boolean isDelegatedSource(Block block) {
        return DELEGATED_CARRIERS.containsKey(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM) || sourceCarriersRegistered) return;
        List<Identifier> fullSources = discoverFullSources();
        List<Identifier> specialSources = discoverSpecialSources();
        List<Identifier> propagationSources = discoverPropagationSources();
        List<Identifier> itemSources = discoverItemSources();
        event.register(Registries.ITEM, helper -> {
            for (Identifier sourceId : fullSources) registerFullSource(helper, sourceId);
            LinkedHashSet<Identifier> delegated = new LinkedHashSet<>(specialSources);
            delegated.addAll(propagationSources);
            for (Identifier sourceId : delegated) registerDelegatedSource(helper, sourceId);
        });
        for (Identifier sourceId : specialSources) {
            Item item = BuiltInRegistries.BLOCK.getValue(sourceId).asItem();
            if (item != Items.AIR) STANDALONE_VARIANT_ITEMS.add(item);
        }
        for (Identifier itemId : itemSources) {
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item != Items.AIR) STANDALONE_VARIANT_ITEMS.add(item);
        }
        FULL_SOURCE_IDS.addAll(fullSources);
        for (Identifier sourceId : fullSources) EXISTING_FORMS.put(sourceId, discoverExistingForms(sourceId));
        SPECIAL_SOURCE_IDS.addAll(specialSources);
        sourceIds = List.copyOf(fullSources);
        specialSourceIds = List.copyOf(specialSources);
        itemSourceIds = List.copyOf(itemSources);
        sourceCarriersRegistered = true;
        ArrayList<Block> all = new ArrayList<>(legacyGenerated());
        all.addAll(SOURCE_CARRIERS);
        generated = Collections.unmodifiableList(all);
        long fullVariants = (long) fullSources.size() * VariantForm.values().length * OxidationStage.values().length * 2L * 17L;
        long standaloneVariants = (long) (specialSources.size() + itemSources.size()) * OxidationStage.values().length * 2L * 17L;
        long states = generated.stream().mapToLong(block -> block.getStateDefinition().getPossibleStates().size()).sum();
        LOGGER.info(
            "Patina Pandemonium exposes {} logical full-block variants and {} standalone variants through {} generated carriers ({} block states)",
            fullVariants, standaloneVariants, SOURCE_CARRIERS.size(), states);
        PatinaRules rules = PatinaRules.INSTANCE;
        if (rules.maximumCreativeTabItems > 0 || rules.maximumCreativePreviewSources > 0) {
            LOGGER.warn(
                "Creative tab previews are limited to {} items and {} sources; set both values to 0 in the rules file to display every source",
                rules.maximumCreativeTabItems, rules.maximumCreativePreviewSources);
        }
    }

    private static void registerFullSource(RegisterEvent.RegisterHelper<Item> helper, Identifier sourceId) {
        Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
        if (source == Blocks.AIR) return;
        EnumMap<VariantForm, Item> items = new EnumMap<>(VariantForm.class);
        for (VariantForm form : VariantForm.values()) {
            Identifier id = sourceCarrierId(sourceId, form);
            Block block = GeneratedBlockFactory.create(id, form, source);
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            Item item = new GeneratedBlockItem(block, form,
                    new Item.Properties().useBlockDescriptionPrefix()
                            .setId(ResourceKey.create(Registries.ITEM, id)));
            helper.register(id, item);
            items.put(form, item);
            BLOCK_SOURCES.put(block, sourceId);
            ITEM_SOURCES.put(item, sourceId);
            SOURCE_CARRIERS.add(block);
            SOURCE_BINDINGS.add(new CarrierBinding(sourceId, form, block, item));
        }
        SOURCE_ITEMS.put(sourceId, items);
    }

    private static void registerDelegatedSource(RegisterEvent.RegisterHelper<Item> helper, Identifier sourceId) {
        Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
        if (source == Blocks.AIR) return;
        Identifier id = delegatedCarrierId(sourceId);
        Block block = GeneratedBlockFactory.createDelegated(id, source);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item item = null;
        if (source.asItem() != Items.AIR) {
            item = new GeneratedBlockItem(block, VariantForm.FULL,
                    new Item.Properties().useBlockDescriptionPrefix()
                            .setId(ResourceKey.create(Registries.ITEM, id)));
            helper.register(id, item);
            DELEGATED_ITEMS.put(sourceId, item);
            DELEGATED_ITEM_SOURCES.put(item, sourceId);
        }
        DELEGATED_CARRIERS.put(sourceId, block);
        BLOCK_SOURCES.put(block, sourceId);
        SOURCE_CARRIERS.add(block);
        SOURCE_BINDINGS.add(new CarrierBinding(sourceId, VariantForm.FULL, block, item));
    }

    private static void onBlockEntityTypeAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        if (!SOURCE_CARRIERS.isEmpty()) event.modify(VARIANT_BLOCK_ENTITY.get(), SOURCE_CARRIERS.toArray(Block[]::new));
    }

    private static List<Identifier> discoverFullSources() {
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

    private static List<Identifier> discoverSpecialSources() {
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

    private static List<Identifier> discoverPropagationSources() {
        ArrayList<Identifier> discovered = new ArrayList<>();
        PatinaRules rules = PatinaRules.INSTANCE;
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            Identifier id = entry.getKey().identifier();
            Block block = entry.getValue();
            if (isCommonBlockSource(id, block, rules) && !(block instanceof EntityBlock)
                && block.getStateDefinition().getPossibleStates().size() <= rules.maximumDelegatedBlockStates
                && PROPAGATION_BLOCK_TYPES.stream().anyMatch(type -> type.isInstance(block))) discovered.add(id);
        });
        discovered.sort(Identifier::compareTo);
        return Collections.unmodifiableList(discovered);
    }

    private static List<Identifier> discoverItemSources() {
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

    private static EnumMap<VariantForm, Block> discoverExistingForms(Identifier sourceId) {
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
        addTrimmedStem(stems, sourcePath, "_planks");
        addTrimmedStem(stems, sourcePath, "_block");
        if (sourcePath.endsWith("s")) stems.add(sourcePath.substring(0, sourcePath.length() - 1));
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

    private static void addTrimmedStem(Set<String> stems, String sourcePath, String suffix) {
        if (sourcePath.endsWith(suffix)) stems.add(sourcePath.substring(0, sourcePath.length() - suffix.length()));
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
        return item != Items.AIR && STANDALONE_VARIANT_ITEMS.contains(item);
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
        ItemVariantData existing = itemData(stack);
        Identifier sourceId = existing == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : existing.sourceId();
        Identifier modelId = existing == null ? stack.get(DataComponents.ITEM_MODEL) : existing.modelId();
        if (modelId == null || VARIANT_ITEM_MODEL.equals(modelId)) modelId = sourceId;
        ItemVariantData data = new ItemVariantData(sourceId, stage, waxed, dye, modelId);
        stack.set(ITEM_VARIANT_DATA.get(), data);
        stack.set(DataComponents.ITEM_MODEL, VARIANT_ITEM_MODEL);
        stack.set(DataComponents.ITEM_NAME, variantItemName(stack, data));
        return stack;
    }

    private static Component variantItemName(ItemStack stack, ItemVariantData data) {
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
        return variantName(data.stageKey(), data.dyeKey(), sourceName, Component.empty());
    }

    public static Component generatedBlockName(ItemStack stack, VariantData data) {
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = Blocks.STONE;
        Component sourceName = DELEGATED_ITEM_SOURCES.containsKey(stack.getItem()) && source.asItem() != Items.AIR
            ? source.asItem().getName(source.asItem().getDefaultInstance()) : source.getName();
        return variantName(data, sourceName);
    }

    public static Component variantName(VariantData data, Component sourceName) {
        return variantName(data.stageKey(), data.dyeKey(), sourceName, Component.translatable(data.formKey()));
    }

    private static Component variantName(String stageKey, String dyeKey, Component sourceName, Component formName) {
        return Component.translatable(
            "item.patina_pandemonium.variant_name",
            Component.translatable(stageKey), Component.translatable(dyeKey), sourceName, formName);
    }

    @Nullable
    private static Item specialSourceItem(Identifier sourceId) {
        if (!SPECIAL_SOURCE_IDS.contains(sourceId)) return null;
        Item item = BuiltInRegistries.BLOCK.getValue(sourceId).asItem();
        return item == Items.AIR ? null : item;
    }

    @Nullable
    private static Item sourceItem(Identifier sourceId, VariantForm form) {
        EnumMap<VariantForm, Item> items = SOURCE_ITEMS.get(sourceId);
        return items == null ? null : items.get(form);
    }

    private static Identifier sourceCarrierId(Identifier sourceId, VariantForm form) {
        return PatinaPandemonium.id("source/" + sourceId.getNamespace() + "/" + sourceId.getPath() + "/" + form.id());
    }

    private static Identifier delegatedCarrierId(Identifier sourceId) {
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

    private static List<Block> legacyGenerated() {
        return List.of(
            FULL.get(), SLAB.get(), STAIRS.get(), WALL.get(), FENCE.get(), FENCE_GATE.get(), CARPET.get(), BUTTON.get(), PRESSURE_PLATE.get(),
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

    public record CarrierBinding(Identifier sourceId, VariantForm form, Block block, @Nullable Item item) {}

}
