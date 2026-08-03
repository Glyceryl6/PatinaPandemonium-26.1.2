package dev.patinapandemonium.client;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantEntry;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.HashMap;
import java.util.Map;

/** Installs flyweight baked block and item models after vanilla model baking has completed. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class PatinaClient {

    private static final Map<VariantForm, Block> TEMPLATES = Map.of(
        VariantForm.FULL, Blocks.STONE,
        VariantForm.SLAB, Blocks.STONE_SLAB,
        VariantForm.STAIRS, Blocks.STONE_STAIRS,
        VariantForm.WALL, Blocks.COBBLESTONE_WALL,
        VariantForm.FENCE, Blocks.OAK_FENCE,
        VariantForm.FENCE_GATE, Blocks.OAK_FENCE_GATE,
        VariantForm.CARPET, Blocks.WHITE_CARPET,
        VariantForm.BUTTON, Blocks.STONE_BUTTON,
        VariantForm.PRESSURE_PLATE, Blocks.STONE_PRESSURE_PLATE);

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelBakery.BakingResult result = event.getBakingResult();
        Map<BlockState, BlockStateModel> blockModels = result.blockStateModels();
        Map<Identifier, ItemModel> itemModels = result.itemStackModels();
        Map<ModelKey, PatinaBlockStateModel> generatedBlockModels = new HashMap<>();
        Map<ModelKey, PatinaItemModel> generatedItemModels = new HashMap<>();
        BlockStateModel fallbackBlock = blockModels.get(Blocks.STONE.defaultBlockState());
        ItemModel fallbackItem = itemModels.get(BuiltInRegistries.ITEM.getKey(Items.STONE));
        PatinaBlockStateModel.clearCache();
        PatinaItemModel.clearCache();
        if (fallbackBlock == null || fallbackItem == null) return;

        for (VariantEntry entry : DynamicVariantRegistry.generated()) {
            Block template = TEMPLATES.get(entry.data().form());
            BlockStateModel sourceModel = blockModels.getOrDefault(entry.source().defaultBlockState(), fallbackBlock);
            BlockStateModel templateModel = blockModels.getOrDefault(template.defaultBlockState(), fallbackBlock);
            Material.Baked sourceMaterial = sourceModel.particleMaterial();
            int tint = entry.data().tint();
            ModelKey key = new ModelKey(entry.source(), entry.data().form(), tint);
            PatinaBlockStateModel blockModel = generatedBlockModels.computeIfAbsent(key, ignored -> new PatinaBlockStateModel(
                blockModels,
                entry.source(),
                template,
                entry.data().form(),
                entry.data().form() == VariantForm.FULL ? sourceModel : templateModel,
                sourceMaterial,
                tint));

            for (BlockState state : entry.block().getStateDefinition().getPossibleStates()) {
                blockModels.put(state, blockModel);
            }

            ItemModel delegate = itemDelegate(itemModels, entry, template, fallbackItem);
            if (delegate != null) {
                PatinaItemModel itemModel = generatedItemModels.computeIfAbsent(key,
                    ignored -> new PatinaItemModel(delegate, fallbackItem, entry.data().form(), sourceMaterial, tint));
                itemModels.put(entry.blockId(), itemModel);
            }
        }
    }

    private static ItemModel itemDelegate(Map<Identifier, ItemModel> itemModels, VariantEntry entry, Block template, ItemModel fallback) {
        Item item = entry.data().form() == VariantForm.FULL ? entry.source().asItem() : template.asItem();
        if (item == Items.AIR) item = template.asItem();
        if (item == Items.AIR) item = Items.STONE;
        return itemModels.getOrDefault(BuiltInRegistries.ITEM.getKey(item), fallback);
    }

    private record ModelKey(Block source, VariantForm form, int tint) {}

}