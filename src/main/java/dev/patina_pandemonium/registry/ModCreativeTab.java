package dev.patina_pandemonium.registry;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.callback.AddCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        CreativeModeTabs.SPAWN_EGGS);
    private static final ResourceKey<CreativeModeTab> MAIN_TAB = tabKey("main");
    private static final ResourceKey<CreativeModeTab> SPECIAL_BLOCKS_TAB = tabKey("special_blocks");
    private static final ResourceKey<CreativeModeTab> LAST_ITEM_CATEGORY_TAB = itemTabKey(ITEM_CATEGORY_TABS.getLast());

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register(MAIN_TAB.identifier().getPath(), ModCreativeTab::mainTab);
        TABS.register(SPECIAL_BLOCKS_TAB.identifier().getPath(), ModCreativeTab::specialBlocksTab);
        ResourceKey<CreativeModeTab> previousTab = SPECIAL_BLOCKS_TAB;
        for (ResourceKey<CreativeModeTab> sourceTab : ITEM_CATEGORY_TABS) {
            ResourceKey<CreativeModeTab> mirrorTab = itemTabKey(sourceTab);
            ResourceKey<CreativeModeTab> predecessor = previousTab;
            TABS.register(mirrorTab.identifier().getPath(), () -> mirroredTab(sourceTab, null, false, predecessor));
            previousTab = mirrorTab;
        }
    }

    public static void register(IEventBus modBus) {
        BuiltInRegistries.CREATIVE_MODE_TAB.addCallback((AddCallback<CreativeModeTab>) ModCreativeTab::onTabAdded);
        TABS.register(modBus);
    }

    private static CreativeModeTab mainTab() {
        return CreativeModeTab.builder().title(Component.translatable("itemGroup.patina_pandemonium"))
                .icon(() -> DynamicVariantRegistry.VARIANT_FABRICATOR_ITEM.get().getDefaultInstance())
                .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                .displayItems((_, output) -> {
                    addFullBlocks(output, false, new int[2]);
                    output.accept(DynamicVariantRegistry.VARIANT_FABRICATOR_ITEM.get());
                }).build();
    }

    private static CreativeModeTab specialBlocksTab() {
        return CreativeModeTab.builder().title(Component.translatable("itemGroup.patina_pandemonium.special_blocks"))
                .icon(Items.CANDLE::getDefaultInstance).withTabsBefore(MAIN_TAB)
                .displayItems((_, output) -> addSpecialBlocks(output, false, new int[2])).build();
    }

    private static CreativeModeTab mirroredTab(ResourceKey<CreativeModeTab> sourceTabKey, CreativeModeTab knownSourceTab, boolean external, ResourceKey<CreativeModeTab> predecessor) {
        CreativeModeTab sourceTab = knownSourceTab == null ? BuiltInRegistries.CREATIVE_MODE_TAB.getValue(sourceTabKey.identifier()) : knownSourceTab;
        Component sourceName = sourceTab == null ? Component.literal(sourceTabKey.identifier().getPath()) : sourceTab.getDisplayName();
        Supplier<ItemStack> icon = sourceTab == null ? Items.IRON_INGOT::getDefaultInstance : () -> variantIcon(sourceTab.getIconItem());
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.patina_pandemonium.category", sourceName))
                .icon(icon).withTabsBefore(predecessor)
                .displayItems((_, output) -> {
                    if (sourceTab == null) return;
                    addCategoryVariants(output, sourceTab, !external, new int[2]);
                }).build();
    }

    private static void onTabAdded(Registry<CreativeModeTab> registry, int id, ResourceKey<CreativeModeTab> sourceKey, CreativeModeTab sourceTab) {
        Identifier sourceId = sourceKey.identifier();
        if (!PatinaRules.INSTANCE.generateExternalVariants
            || sourceTab.getType() != CreativeModeTab.Type.CATEGORY
            || sourceId.getNamespace().equals("minecraft")
            || sourceId.getNamespace().equals(PatinaPandemonium.MOD_ID)) return;
        Identifier mirrorId = PatinaPandemonium.id("compat/" + sourceId.getNamespace() + "/" + sourceId.getPath());
        if (registry.containsKey(mirrorId)) return;
        Registry.register(registry, mirrorId, mirroredTab(sourceKey, sourceTab, true, LAST_ITEM_CATEGORY_TAB));
    }

    private static ItemStack variantIcon(ItemStack source) {
        if (!DynamicVariantRegistry.supportsFabrication(source)) return source.copy();
        ItemStack variant = DynamicVariantRegistry.fabricate(
            source, VariantForm.FULL, OxidationStage.OXIDIZED, false, null, 1);
        return variant.isEmpty() ? source.copy() : variant;
    }

    private static void addFullBlocks(CreativeModeTab.Output output, boolean external, int[] counts) {
        for (Identifier sourceId : DynamicVariantRegistry.sourceIds()) {
            if (DynamicVariantRegistry.isExternal(sourceId) != external || !canAddSource(counts)) continue;
            if (!addFullSourceVariants(output, sourceId, counts)) break;
            counts[1]++;
        }
    }

    private static boolean addFullSourceVariants(CreativeModeTab.Output output, Identifier sourceId, int[] counts) {
        int variants = fullVariantCount(sourceId);
        if (!canAddItems(counts, variants)) return false;
        for (OxidationStage stage : OxidationStage.values()) {
            for (boolean waxed : WAX_STATES) {
                for (VariantForm form : VariantForm.values()) {
                    if (isExistingPristineForm(sourceId, form, stage, waxed)) continue;
                    output.accept(DynamicVariantRegistry.displayStack(new VariantData(sourceId, stage, waxed, form, null)));
                    counts[0]++;
                }
            }
        }

        return true;
    }

    private static int fullVariantCount(Identifier sourceId) {
        int count = 0;
        for (OxidationStage stage : OxidationStage.values()) {
            for (boolean waxed : WAX_STATES) {
                for (VariantForm form : VariantForm.values()) {
                    if (!isExistingPristineForm(sourceId, form, stage, waxed)) count++;
                }
            }
        }

        return count;
    }

    private static boolean isExistingPristineForm(Identifier sourceId, VariantForm form, OxidationStage stage, boolean waxed) {
        return form != VariantForm.FULL && stage == OxidationStage.FRESH && !waxed && DynamicVariantRegistry.hasExistingForm(sourceId, form);
    }

    private static void addSpecialBlocks(CreativeModeTab.Output output, boolean external, int[] counts) {
        for (Identifier sourceId : DynamicVariantRegistry.specialSourceIds()) {
            if (DynamicVariantRegistry.isExternal(sourceId) != external || !canAddSource(counts)) continue;
            Block source = BuiltInRegistries.BLOCK.getValue(sourceId);
            if (source == Blocks.AIR || source.asItem() == Items.AIR) continue;
            if (!addStandaloneVariants(output, new ItemStack(source.asItem()), counts)) break;
            counts[1]++;
        }
    }

    private static void addCategoryVariants(CreativeModeTab.Output output, CreativeModeTab sourceTab, boolean includeVanillaItems, int[] counts) {
        Set<Identifier> fullSources = new HashSet<>();
        Set<Item> blockSources = new HashSet<>();
        for (ItemStack source : sourceTab.getDisplayItems()) {
            Item item = source.getItem();
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId.getNamespace().equals(PatinaPandemonium.MOD_ID) || !canAddSource(counts)) continue;
            if (!DynamicVariantRegistry.isExternal(itemId)) {
                if (!includeVanillaItems || item instanceof BlockItem || !DynamicVariantRegistry.isStandaloneVariantItem(item)) continue;
                if (!addStandaloneVariants(output, source.copyWithCount(1), counts)) break;
                counts[1]++;
                continue;
            }

            if (!PatinaRules.INSTANCE.generateExternalVariants) continue;
            Identifier fullSource = DynamicVariantRegistry.fullSourceId(source);
            if (fullSource != null) {
                if (!fullSources.add(fullSource)) continue;
                if (!addFullSourceVariants(output, fullSource, counts)) break;
                counts[1]++;
                continue;
            }

            if (!DynamicVariantRegistry.isStandaloneVariantItem(item)) continue;
            if (item instanceof BlockItem && !blockSources.add(item)) continue;
            if (!addStandaloneVariants(output, source.copyWithCount(1), counts)) break;
            counts[1]++;
        }
    }

    private static boolean addStandaloneVariants(CreativeModeTab.Output output, ItemStack source, int[] counts) {
        int variants = OxidationStage.values().length * WAX_STATES.length;
        if (!canAddItems(counts, variants)) return false;
        for (OxidationStage stage : OxidationStage.values()) {
            for (boolean waxed : WAX_STATES) {
                output.accept(DynamicVariantRegistry.fabricate(source, VariantForm.FULL, stage, waxed, null, 1));
                counts[0]++;
            }
        }

        return true;
    }

    private static boolean canAddSource(int[] counts) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCreativePreviewSources);
        return maximum == 0 || counts[1] < maximum;
    }

    private static boolean canAddItems(int[] counts, int addition) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCreativeTabItems);
        return maximum == 0 || counts[0] + addition <= maximum;
    }

    private static ResourceKey<CreativeModeTab> itemTabKey(ResourceKey<CreativeModeTab> sourceTab) {
        return tabKey("items/" + sourceTab.identifier().getPath());
    }

    private static ResourceKey<CreativeModeTab> tabKey(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, PatinaPandemonium.id(path));
    }

}