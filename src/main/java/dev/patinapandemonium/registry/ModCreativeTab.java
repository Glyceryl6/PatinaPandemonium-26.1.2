package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

public class ModCreativeTab {

    private static final boolean[] WAX_STATES = {false, true};
    private static final List<ResourceKey<CreativeModeTab>> ITEM_CATEGORY_TABS = List.of(
        CreativeModeTabs.BUILDING_BLOCKS,
        CreativeModeTabs.COLORED_BLOCKS,
        CreativeModeTabs.NATURAL_BLOCKS,
        CreativeModeTabs.FUNCTIONAL_BLOCKS,
        CreativeModeTabs.REDSTONE_BLOCKS,
        CreativeModeTabs.TOOLS_AND_UTILITIES,
        CreativeModeTabs.COMBAT,
        CreativeModeTabs.FOOD_AND_DRINKS,
        CreativeModeTabs.INGREDIENTS,
        CreativeModeTabs.SPAWN_EGGS,
        CreativeModeTabs.OP_BLOCKS);

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register("main", () -> tab(Component.translatable("itemGroup.patina_pandemonium"), Items.COPPER_BLOCK::getDefaultInstance,
            (_, output) -> {
                int[] counts = new int[2];
                addFullBlocks(output, false, counts);
                output.accept(DynamicVariantRegistry.VARIANT_FABRICATOR_ITEM.get());
            }));
        TABS.register("special_blocks", () -> tab(
            Component.translatable("itemGroup.patina_pandemonium.special_blocks"), Items.CANDLE::getDefaultInstance,
            (_, output) -> addSpecialBlocks(output, false, new int[2])));
        for (ResourceKey<CreativeModeTab> sourceTab : ITEM_CATEGORY_TABS) {
            String path = "items/" + sourceTab.identifier().getPath();
            TABS.register(path, () -> mirroredItemTab(sourceTab));
        }
        TABS.register("external_blocks", () -> tab(
            Component.translatable("itemGroup.patina_pandemonium.external_blocks"), Items.CHEST::getDefaultInstance,
            (_, output) -> {
                int[] counts = new int[2];
                addFullBlocks(output, true, counts);
                addSpecialBlocks(output, true, counts);
            }));
        TABS.register("external_items", () -> tab(
            Component.translatable("itemGroup.patina_pandemonium.external_items"), Items.BUNDLE::getDefaultInstance,
            (_, output) -> addExternalItems(output, new int[2])));
    }

    private static CreativeModeTab mirroredItemTab(ResourceKey<CreativeModeTab> sourceTabKey) {
        CreativeModeTab sourceTab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(sourceTabKey.identifier());
        Component sourceName = sourceTab == null ? Component.literal(sourceTabKey.identifier().getPath()) : sourceTab.getDisplayName();
        Supplier<ItemStack> icon = sourceTab == null ? Items.IRON_INGOT::getDefaultInstance : () -> sourceTab.getIconItem().copy();
        return tab(Component.translatable("itemGroup.patina_pandemonium.items_category", sourceName), icon,
            (_, output) -> addVanillaItems(output, sourceTab, new int[2]));
    }

    private static CreativeModeTab tab(Component title, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator generator) {
        return CreativeModeTab.builder().title(title).icon(icon).displayItems(generator).build();
    }

    private static void addFullBlocks(CreativeModeTab.Output output, boolean external, int[] counts) {
        for (Identifier sourceId : DynamicVariantRegistry.sourceIds()) {
            if (DynamicVariantRegistry.isExternal(sourceId) != external || !canAddSource(counts)) continue;
            int variants = VariantForm.values().length * OxidationStage.values().length * 2;
            if (!canAddItems(counts, variants)) break;
            for (OxidationStage stage : OxidationStage.values()) {
                for (boolean waxed : WAX_STATES) {
                    for (VariantForm form : VariantForm.values()) {
                        output.accept(DynamicVariantRegistry.displayStack(new VariantData(sourceId, stage, waxed, form, null)));
                        counts[0]++;
                    }
                }
            }

            counts[1]++;
        }
    }

    private static void addSpecialBlocks(CreativeModeTab.Output output, boolean external, int[] counts) {
        for (Identifier sourceId : DynamicVariantRegistry.specialSourceIds()) {
            if (DynamicVariantRegistry.isExternal(sourceId) != external || !canAddSource(counts)) continue;
            int variants = OxidationStage.values().length * 2;
            if (!canAddItems(counts, variants)) break;
            Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
            if (source == Blocks.AIR || source.asItem() == Items.AIR) continue;
            addStandaloneVariants(output, new ItemStack(source.asItem()), counts);
            counts[1]++;
        }
    }

    private static void addVanillaItems(CreativeModeTab.Output output, CreativeModeTab sourceTab, int[] counts) {
        if (sourceTab == null) return;
        for (ItemStack source : sourceTab.getDisplayItems()) {
            Identifier sourceId = BuiltInRegistries.ITEM.getKey(source.getItem());
            if (!sourceId.getNamespace().equals("minecraft") || !DynamicVariantRegistry.isStandaloneVariantItem(source.getItem())
                || !canAddSource(counts)) continue;
            int variants = OxidationStage.values().length * 2;
            if (!canAddItems(counts, variants)) break;
            addStandaloneVariants(output, source.copyWithCount(1), counts);
            counts[1]++;
        }
    }

    private static void addExternalItems(CreativeModeTab.Output output, int[] counts) {
        for (Identifier sourceId : DynamicVariantRegistry.itemSourceIds()) {
            if (!DynamicVariantRegistry.isExternal(sourceId) || !canAddSource(counts)) continue;
            int variants = OxidationStage.values().length * 2;
            if (!canAddItems(counts, variants)) break;
            Item source = BuiltInRegistries.ITEM.getValue(sourceId);
            if (source == Items.AIR) continue;
            addStandaloneVariants(output, new ItemStack(source), counts);
            counts[1]++;
        }
    }

    private static void addStandaloneVariants(CreativeModeTab.Output output, ItemStack source, int[] counts) {
        for (OxidationStage stage : OxidationStage.values()) {
            for (boolean waxed : WAX_STATES) {
                output.accept(DynamicVariantRegistry.fabricate(source, VariantForm.FULL, stage, waxed, null, 1));
                counts[0]++;
            }
        }
    }

    private static boolean canAddSource(int[] counts) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCreativePreviewSources);
        return maximum == 0 || counts[1] < maximum;
    }

    private static boolean canAddItems(int[] counts, int addition) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCreativeTabItems);
        return maximum == 0 || counts[0] + addition <= maximum;
    }

}