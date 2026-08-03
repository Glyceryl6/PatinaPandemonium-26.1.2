package dev.patinapandemonium.registry;

import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.block.VariantFabricatorBlock;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.block.entity.VariantFabricatorBlockEntity;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import dev.patinapandemonium.menu.VariantFabricatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registers nine logical forms with separate occluding and non-occluding flyweight carriers. Source, dye, oxidation and wax are values stored on the
 * stack or placed block entity, so logical combinations do not multiply registry entries.
 */
public class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PatinaPandemonium.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PatinaPandemonium.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
        Registries.BLOCK_ENTITY_TYPE, PatinaPandemonium.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PatinaPandemonium.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantData>> VARIANT_DATA =
            COMPONENTS.registerComponentType("variant_data", builder -> builder.persistent(VariantData.CODEC));

    public static final DeferredBlock<VariantFabricatorBlock> VARIANT_FABRICATOR = BLOCKS.register(
        "variant_fabricator",
        id -> new VariantFabricatorBlock(BlockBehaviour.Properties.of()
            .strength(3.5F, 6.0F)
            .sound(SoundType.COPPER)
            .setId(ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredItem<BlockItem> VARIANT_FABRICATOR_ITEM = ITEMS.registerItem(
        "variant_fabricator",
        properties -> new BlockItem(VARIANT_FABRICATOR.get(), properties.useBlockDescriptionPrefix()));

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatinaVariantBlockEntity>> VARIANT_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("virtual_variant", () -> new BlockEntityType<>(
            PatinaVariantBlockEntity::new, false, generated().toArray(Block[]::new)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VariantFabricatorBlockEntity>> VARIANT_FABRICATOR_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("variant_fabricator", () -> new BlockEntityType<>(
            VariantFabricatorBlockEntity::new, false, VARIANT_FABRICATOR.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<VariantFabricatorMenu>> VARIANT_FABRICATOR_MENU = MENUS.register(
        "variant_fabricator", () -> new MenuType<>(VariantFabricatorMenu::new, FeatureFlags.VANILLA_SET));

    private static volatile List<Identifier> sourceIds;

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
    }

    /** Kept for the obsolete optional exporter; virtual variants intentionally have no per-combination JSON entries. */
    public static List<VariantEntry> entries() {
        return List.of();
    }

    public static List<Block> generated() {
        return List.of(
            FULL.get(), SLAB.get(), STAIRS.get(), WALL.get(), FENCE.get(), FENCE_GATE.get(), CARPET.get(), BUTTON.get(), PRESSURE_PLATE.get(),
            TRANSLUCENT_FULL.get(), TRANSLUCENT_SLAB.get(), TRANSLUCENT_STAIRS.get(), TRANSLUCENT_WALL.get(), TRANSLUCENT_FENCE.get(),
            TRANSLUCENT_FENCE_GATE.get(), TRANSLUCENT_CARPET.get(), TRANSLUCENT_BUTTON.get(), TRANSLUCENT_PRESSURE_PLATE.get());
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
        return (data == null ? VariantData.defaultFor(form) : data).normalized(form);
    }

    public static ItemStack stack(VariantData data) {
        return stack(data, 1);
    }

    public static ItemStack displayStack(VariantData data) {
        return displayStack(data, 1);
    }

    public static ItemStack displayStack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        if (normalized.form() == VariantForm.FULL && normalized.stage() == OxidationStage.FRESH
            && !normalized.waxed() && normalized.dyeColor() == null) {
            Block source = BuiltInRegistries.BLOCK.getValue(normalized.sourceId());
            if (source != null && source != Blocks.AIR && source.asItem() != Items.AIR) {
                return new ItemStack(source, Math.max(1, count));
            }
        }

        return stack(normalized, count);
    }

    public static ItemStack stack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        ItemStack stack = new ItemStack(carrierItem(normalized.form(), requiresNonOccludingCarrier(normalized.sourceId())), Math.max(1, count));
        stack.set(VARIANT_DATA.get(), normalized);
        return stack;
    }

    public static List<Identifier> sourceIds() {
        List<Identifier> cached = sourceIds;
        if (cached != null) return cached;
        synchronized (DynamicVariantRegistry.class) {
            if (sourceIds != null) return sourceIds;
            ArrayList<Identifier> discovered = new ArrayList<>();
            PatinaRules rules = PatinaRules.INSTANCE;
            BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
                if (isSource(entry.getKey().identifier(), entry.getValue(), rules)) discovered.add(entry.getKey().identifier());
            });
            discovered.sort(Identifier::compareTo);
            sourceIds = Collections.unmodifiableList(discovered);
            long variants = (long) sourceIds.size() * VariantForm.values().length * OxidationStage.values().length * 2L * 17L;
            int states = generated().stream().mapToInt(block -> block.getStateDefinition().getPossibleStates().size()).sum();
            LOGGER.info(
                "Patina Pandemonium exposes {} logical variants from {} full-block sources through {} carriers and {} carrier states",
                variants, sourceIds.size(), generated().size(), states);
            if (rules.maximumCreativeTabItems > 0 || rules.maximumCreativePreviewSources > 0) {
                LOGGER.warn(
                    "Creative tab previews are limited to {} items and {} source blocks; set both values to 0 in the rules file to display every source",
                    rules.maximumCreativeTabItems, rules.maximumCreativePreviewSources);
            }
            return sourceIds;
        }
    }

    public static boolean isSource(Identifier id) {
        return isSource(id, BuiltInRegistries.BLOCK.getValue(id), PatinaRules.INSTANCE);
    }

    public static boolean isSource(Identifier id, Block block, PatinaRules rules) {
        if (!rules.namespaceAllowed(id.getNamespace())
            || rules.excludedBlocks != null && rules.excludedBlocks.contains(id.toString())
            || block == null
            || block == Blocks.AIR) return false;
        if (block instanceof SlabBlock
            || block instanceof StairBlock
            || block instanceof WallBlock
            || block instanceof FenceBlock
            || block instanceof FenceGateBlock
            || block instanceof CarpetBlock
            || block instanceof ButtonBlock
            || block instanceof PressurePlateBlock
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock) return false;

        BlockState state = block.defaultBlockState();
        try {
            return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                || Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return state.canOcclude();
        }
    }

    private static DeferredBlock<Block> carrier(String name, VariantForm form) {
        return carrier(name, form, false);
    }

    private static DeferredBlock<Block> carrier(String name, VariantForm form, boolean nonOccluding) {
        return BLOCKS.register(name, id -> GeneratedBlockFactory.create(id, form, nonOccluding));
    }

    private static DeferredItem<GeneratedBlockItem> carrierItem(String name, Supplier<? extends Block> block, VariantForm form) {
        return ITEMS.registerItem(
            name,
            properties -> new GeneratedBlockItem(block.get(), form, properties),
            properties -> properties.useBlockDescriptionPrefix());
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
        return source != null && source != Blocks.AIR && !source.defaultBlockState().canOcclude();
    }

}