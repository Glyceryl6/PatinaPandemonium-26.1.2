package dev.patina_pandemonium.registry;

import dev.patina_pandemonium.block.GeneratedBlockFactory;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.item.GeneratedBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static dev.patina_pandemonium.registry.DynamicVariantRegistry.*;

/** Owns runtime source discovery and carrier registration. */
class VariantSourceRegistration {

    static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM) || sourceCarriersRegistered) return;
        List<Identifier> fullSources = discoverFullSources();
        List<Identifier> specialSources = discoverSpecialSources();
        List<Identifier> itemSources = discoverItemSources();
        event.register(Registries.ITEM, helper -> {
            for (Identifier sourceId : fullSources) registerFullSource(helper, sourceId);
            for (Identifier sourceId : specialSources) {
                Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
                if (source instanceof EntityBlock) NATIVE_BLOCK_ENTITY_SOURCE_IDS.add(sourceId);
                else registerDelegatedSource(helper, sourceId);
            }
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
        for (Identifier sourceId : fullSources) {
            EnumMap<VariantForm, Block> forms = discoverExistingForms(sourceId);
            EXISTING_FORMS.put(sourceId, forms);
            forms.forEach((form, block) -> {
                if (block.asItem() != Items.AIR) {
                    EXISTING_FORM_OUTPUTS.put(block.asItem(), new ExistingFormBinding(sourceId, form));
                }
            });
        }

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

    static void registerFullSource(RegisterEvent.RegisterHelper<Item> helper, Identifier sourceId) {
        Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
        if (source == Blocks.AIR) return;
        boolean nativeBlockEntity = source instanceof EntityBlock;
        if (nativeBlockEntity) {
            NATIVE_BLOCK_ENTITY_SOURCE_IDS.add(sourceId);
            if (source.asItem() != Items.AIR) STANDALONE_VARIANT_ITEMS.add(source.asItem());
        }

        EnumMap<VariantForm, Item> items = new EnumMap<>(VariantForm.class);
        EnumMap<VariantForm, Block> blocks = new EnumMap<>(VariantForm.class);
        for (VariantForm form : VariantForm.values()) {
            if (nativeBlockEntity && form == VariantForm.FULL) continue;
            Identifier id = sourceCarrierId(sourceId, form);
            Block block = GeneratedBlockFactory.create(id, form, source);
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            Item item = new GeneratedBlockItem(block, form,
                    new Item.Properties().useBlockDescriptionPrefix()
                            .setId(ResourceKey.create(Registries.ITEM, id)));
            helper.register(id, item);
            items.put(form, item);
            blocks.put(form, block);
            BLOCK_SOURCES.put(block, sourceId);
            ITEM_SOURCES.put(item, sourceId);
            SOURCE_CARRIERS.add(block);
            SOURCE_BINDINGS.add(new CarrierBinding(sourceId, form, block, item));
        }

        SOURCE_ITEMS.put(sourceId, items);
        SOURCE_BLOCKS.put(sourceId, blocks);
    }

    static void registerDelegatedSource(RegisterEvent.RegisterHelper<Item> helper, Identifier sourceId) {
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

    static void onStaticItemsRegistered(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;
        Item cauldron = SEEDED_BREWING_CAULDRON_ITEM.get();
        STANDALONE_VARIANT_ITEMS.add(cauldron);
        NATIVE_BLOCK_ENTITY_SOURCE_IDS.add(BuiltInRegistries.BLOCK.getKey(SEEDED_BREWING_CAULDRON.get()));
    }

    static void onBlockEntityTypeAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        if (SOURCE_CARRIERS.isEmpty()) return;
        Identifier craftingTableId = BuiltInRegistries.BLOCK.getKey(Blocks.CRAFTING_TABLE);
        Block[] craftingTables = SOURCE_BINDINGS.stream().filter(binding -> binding.sourceId().equals(craftingTableId))
            .map(CarrierBinding::block).toArray(Block[]::new);
        Block[] generic = SOURCE_CARRIERS.stream().filter(block -> !craftingTableId.equals(BLOCK_SOURCES.get(block))).toArray(Block[]::new);
        if (generic.length > 0) event.modify(VARIANT_BLOCK_ENTITY.get(), generic);
        if (craftingTables.length > 0) event.modify(LINEAGE_CRAFTING_TABLE_BLOCK_ENTITY.get(), craftingTables);
    }

}