package dev.patinapandemonium.registry;

import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registers nine flyweight carrier blocks. Source, dye, oxidation and wax are values stored on the
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VariantData>> VARIANT_DATA =
            COMPONENTS.registerComponentType("variant_data", builder -> builder.persistent(VariantData.CODEC));

    public static final DeferredBlock<Block> FULL = carrier("virtual_full", VariantForm.FULL);
    public static final DeferredBlock<Block> SLAB = carrier("virtual_slab", VariantForm.SLAB);
    public static final DeferredBlock<Block> STAIRS = carrier("virtual_stairs", VariantForm.STAIRS);
    public static final DeferredBlock<Block> WALL = carrier("virtual_wall", VariantForm.WALL);
    public static final DeferredBlock<Block> FENCE = carrier("virtual_fence", VariantForm.FENCE);
    public static final DeferredBlock<Block> FENCE_GATE = carrier("virtual_fence_gate", VariantForm.FENCE_GATE);
    public static final DeferredBlock<Block> CARPET = carrier("virtual_carpet", VariantForm.CARPET);
    public static final DeferredBlock<Block> BUTTON = carrier("virtual_button", VariantForm.BUTTON);
    public static final DeferredBlock<Block> PRESSURE_PLATE = carrier("virtual_pressure_plate", VariantForm.PRESSURE_PLATE);

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

    private static final Map<VariantForm, Supplier<? extends Block>> CARRIERS = carriers();
    private static final Map<VariantForm, Supplier<? extends Item>> CARRIER_ITEMS = carrierItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatinaVariantBlockEntity>> VARIANT_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("virtual_variant", () -> new BlockEntityType<>(
                    PatinaVariantBlockEntity::new, false, generated().toArray(Block[]::new)));

    private static volatile List<Identifier> sourceIds;

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }

    /**
     * Kept for the obsolete optional exporter; virtual variants intentionally have no per-combination JSON entries.
     */
    public static List<VariantEntry> entries() {
        return List.of();
    }

    public static List<Block> generated() {
        return List.of(
                FULL.get(), SLAB.get(), STAIRS.get(), WALL.get(), FENCE.get(),
                FENCE_GATE.get(), CARPET.get(), BUTTON.get(), PRESSURE_PLATE.get());
    }

    public static Block carrier(VariantForm form) {
        return CARRIERS.get(form).get();
    }

    public static Item carrierItem(VariantForm form) {
        return CARRIER_ITEMS.get(form).get();
    }

    public static VariantData data(ItemStack stack, VariantForm form) {
        VariantData data = stack.get(VARIANT_DATA.get());
        return (data == null ? VariantData.defaultFor(form) : data).normalized(form);
    }

    public static ItemStack stack(VariantData data) {
        return stack(data, 1);
    }

    public static ItemStack stack(VariantData data, int count) {
        VariantData normalized = data.normalized(data.form());
        ItemStack stack = new ItemStack(carrierItem(normalized.form()), Math.max(1, count));
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
                if (isSource(entry.getKey().identifier(), entry.getValue(), rules))
                    discovered.add(entry.getKey().identifier());
            });
            discovered.sort(Identifier::compareTo);
            sourceIds = Collections.unmodifiableList(discovered);
            long variants = (long) sourceIds.size() * VariantForm.values().length * OxidationStage.values().length * 2L * 17L;
            int states = generated().stream().mapToInt(block -> block.getStateDefinition().getPossibleStates().size()).sum();
            LOGGER.info(
                    "Patina Pandemonium exposes {} logical variants from {} full-block sources through {} carriers and {} carrier states",
                    variants, sourceIds.size(), generated().size(), states);
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
        return BLOCKS.register(name, id -> GeneratedBlockFactory.create(id, form));
    }

    private static DeferredItem<GeneratedBlockItem> carrierItem(String name, Supplier<? extends Block> block, VariantForm form) {
        return ITEMS.registerItem(name,
                properties -> new GeneratedBlockItem(block.get(), form, properties),
                Item.Properties::useBlockDescriptionPrefix);
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

}