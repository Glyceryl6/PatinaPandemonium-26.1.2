package dev.patinapandemonium.registry;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.GeneratedBlockFactory;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.item.GeneratedBlockItem;
import dev.patinapandemonium.item.GeneratedSignItem;
import dev.patinapandemonium.resource.RuntimePack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.*;

public final class DynamicVariantRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<VariantEntry> ENTRIES = new ArrayList<>();
    private static final List<VariantEntry> GENERATED = new ArrayList<>();
    private static final Set<Identifier> PROCESSED_SOURCES = new LinkedHashSet<>();
    private static boolean blocksDone;

    public static List<VariantEntry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static List<VariantEntry> generated() {
        return List.copyOf(GENERATED);
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            registerBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            registerItems(event);
        }
    }

    public static void onBlockEntityTypeAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        Block[] signs = GENERATED.stream()
                .filter(entry -> entry.data().form() == VariantForm.SIGN || entry.data().form() == VariantForm.WALL_SIGN)
                .map(VariantEntry::block).toArray(Block[]::new);
        event.modify(BlockEntityType.SIGN, signs);
    }

    private static void registerBlocks(RegisterEvent event) {
        if (blocksDone) {
            return;
        }
        blocksDone = true;
        PatinaRules rules = PatinaRules.INSTANCE;
        List<Map.Entry<ResourceKey<Block>, Block>> snapshot = new ArrayList<>(BuiltInRegistries.BLOCK.entrySet());
        List<Map.Entry<ResourceKey<Block>, Block>> sources = snapshot.stream()
                .filter(entry -> isSource(entry.getKey().identifier(), entry.getValue(), rules)).toList();
        for (Map.Entry<ResourceKey<Block>, Block> sourceEntry : sources) {
            registerFamily(event, sourceEntry.getKey().identifier(), sourceEntry.getValue(), rules);
        }

        RuntimePack.rebuild(ENTRIES);
        LOGGER.info(
                "Patina Pandemonium found {} full-block sources and registered {} missing blocks",
                PROCESSED_SOURCES.size(), GENERATED.size());
    }

    private static void registerFamily(RegisterEvent event, Identifier sourceId, Block source, PatinaRules rules) {
        if (!PROCESSED_SOURCES.add(sourceId)) {
            return;
        }

        Map<String, Block> family = new LinkedHashMap<>();
        for (boolean waxed : new boolean[]{false, true}) {
            for (OxidationStage stage : OxidationStage.values()) {
                Block stageBase = null;
                for (VariantForm form : VariantForm.values()) {
                    if (!form.enabled(rules)) {
                        continue;
                    }

                    VariantData data = new VariantData(sourceId, stage, waxed, form);
                    Identifier existingId = findExisting(data, rules);
                    Block block = existingId == null ? null : BuiltInRegistries.BLOCK.getValue(existingId);
                    boolean generated = block == null || block == Blocks.AIR;
                    Identifier blockId;

                    if (form == VariantForm.FULL && stage == OxidationStage.FRESH && !waxed) {
                        block = source;
                        blockId = sourceId;
                        generated = false;
                    } else if (generated) {
                        if (GENERATED.size() >= rules.maximumGeneratedBlocks) {
                            throw new IllegalStateException("Patina Pandemonium generation limit exceeded: " + rules.maximumGeneratedBlocks);
                        }

                        blockId = generatedId(sourceId, stage, waxed, form);
                        Block base = stageBase == null ? source : stageBase;
                        Block made = GeneratedBlockFactory.create(blockId, source, base, data);
                        event.register(Registries.BLOCK, blockId, () -> made);
                        block = made;
                    } else {
                        blockId = existingId;
                    }

                    if (form == VariantForm.FULL) {
                        stageBase = block;
                    }

                    VariantEntry entry = new VariantEntry(data, blockId, block, source, generated);
                    ENTRIES.add(entry);
                    family.put(key(stage, waxed, form), block);
                    if (generated) {
                        GENERATED.add(entry);
                    }
                }
            }
        }

        for (VariantForm form : VariantForm.values()) {
            if (!form.enabled(rules)) {
                continue;
            }
            for (OxidationStage stage : OxidationStage.values()) {
                OxidationStage next = stage.next();
                if (next == null) {
                    continue;
                }
                Block from = family.get(key(stage, false, form));
                Block to = family.get(key(next, false, form));
                if (from != null && to != null) {
                    VariantRuntime.link(from, to);
                }
            }
        }
    }

    private static void registerItems(RegisterEvent event) {
        Map<String, VariantEntry> lookup = new LinkedHashMap<>();
        for (VariantEntry entry : ENTRIES) {
            lookup.put(entryKey(entry.data()), entry);
        }

        for (VariantEntry entry : GENERATED) {
            if (!entry.data().form().hasItem()) {
                continue;
            }

            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, entry.blockId());
            Item.Properties properties = new Item.Properties().setId(key);
            Item item;

            if (entry.data().form() == VariantForm.SIGN) {
                VariantData wallData = new VariantData(
                        entry.data().sourceId(),
                        entry.data().stage(),
                        entry.data().waxed(),
                        VariantForm.WALL_SIGN);
                VariantEntry wall = lookup.get(entryKey(wallData));
                if (wall == null) {
                    throw new IllegalStateException("Missing wall sign partner for " + entry.blockId());
                }

                item = new GeneratedSignItem(
                        entry.block(),
                        wall.block(),
                        entry.source(),
                        entry.data(),
                        properties);
            } else {
                item = new GeneratedBlockItem(entry.block(), entry.source(), entry.data(), properties);
            }

            event.register(Registries.ITEM, entry.blockId(), () -> item);
        }
    }

    private static boolean isSource(Identifier id, Block block, PatinaRules rules) {
        if (!rules.namespaceAllowed(id.getNamespace())
                || (rules.excludedBlocks != null && rules.excludedBlocks.contains(id.toString()))
                || block == Blocks.AIR) {
            return false;
        }

        if (block instanceof SlabBlock
                || block instanceof StairBlock
                || block instanceof WallBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof PressurePlateBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof SignBlock) {
            return false;
        }

        String path = id.getPath();
        if (path.startsWith("exposed_")
                || path.startsWith("weathered_")
                || path.startsWith("oxidized_")
                || path.startsWith("waxed_")) {
            return false;
        }

        BlockState state = block.defaultBlockState();
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Identifier generatedId(Identifier source, OxidationStage stage, boolean waxed, VariantForm form) {
        return PatinaPandemonium.id(
                "generated/" + source.getNamespace() + "/" + source.getPath() + "/"
                        + (waxed ? "waxed_" : "") + stage.id() + "/" + form.id());
    }

    private static String key(OxidationStage stage, boolean waxed, VariantForm form) {
        return stage.id() + ":" + waxed + ":" + form.id();
    }

    private static String entryKey(VariantData data) {
        return data.sourceId() + "|" + data.stage().id() + "|" + data.waxed() + "|" + data.form().id();
    }

    private static Identifier findExisting(VariantData data, PatinaRules rules) {
        if (data.stage() == OxidationStage.FRESH && !data.waxed() && data.form() == VariantForm.FULL) {
            return data.sourceId();
        }

        JsonElement override = rules.existingFormOverrides == null ? null : rules.existingFormOverrides.get(entryKey(data));
        if (override != null && override.isJsonPrimitive()) {
            Identifier overrideId = Identifier.tryParse(override.getAsString());
            if (overrideId != null && BuiltInRegistries.BLOCK.containsKey(overrideId)) {
                return overrideId;
            }
        }

        String stagePrefix = data.stage() == OxidationStage.FRESH ? "" : data.stage().id() + "_";
        String waxPrefix = data.waxed() ? "waxed_" : "";
        for (String stem : candidateStems(data.sourceId().getPath())) {
            String candidate = waxPrefix + stagePrefix + stem + data.form().suffix();
            Identifier id = Identifier.tryBuild(data.sourceId().getNamespace(), candidate);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return id;
            }
        }

        return null;
    }

    private static List<String> candidateStems(String path) {
        LinkedHashSet<String> stems = new LinkedHashSet<>();
        stems.add(path);
        stripSuffix(stems, path, "_block");
        stripSuffix(stems, path, "_planks");
        stripSuffix(stems, path, "_bricks");
        stripSuffix(stems, path, "_tiles");
        if (path.equals("bricks")) {
            stems.add("brick");
        }
        if (path.endsWith("_bricks")) {
            stems.add(path.substring(0, path.length() - 1));
        }
        if (path.endsWith("_tiles")) {
            stems.add(path.substring(0, path.length() - 1));
        }
        return List.copyOf(stems);
    }

    private static void stripSuffix(Set<String> stems, String path, String suffix) {
        if (path.endsWith(suffix) && path.length() > suffix.length()) {
            stems.add(path.substring(0, path.length() - suffix.length()));
        }
    }

}