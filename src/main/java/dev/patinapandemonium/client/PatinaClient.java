package dev.patinapandemonium.client;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
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
        Map<ModelKey, GeneratedModels> generatedModels = new HashMap<>();
        BlockStateModel fallbackBlock = blockModels.get(Blocks.STONE.defaultBlockState());
        ItemModel fallbackItem = itemModels.get(BuiltInRegistries.ITEM.getKey(Items.STONE));
        PatinaBlockStateModel.clearCache();
        PatinaItemModel.clearCache();
        if (fallbackBlock == null || fallbackItem == null) return;

        for (Block block : DynamicVariantRegistry.generated()) {
            VariantData data = ((PatinaOxidizable) block).patinaData();
            Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
            if (source == Blocks.AIR) source = Blocks.STONE;
            Block template = TEMPLATES.get(data.form());
            BlockStateModel sourceModel = blockModels.getOrDefault(source.defaultBlockState(), fallbackBlock);
            BlockStateModel templateModel = blockModels.getOrDefault(template.defaultBlockState(), fallbackBlock);
            Material.Baked sourceMaterial = sourceModel.particleMaterial();
            int tint = data.tint();
            ModelKey key = new ModelKey(source, data.form(), tint);
            Block modelSource = source;
            GeneratedModels models = generatedModels.computeIfAbsent(key, ignored -> new GeneratedModels(
                new PatinaBlockStateModel(
                    blockModels,
                    modelSource,
                    template,
                    data.form(),
                    data.form() == VariantForm.FULL ? sourceModel : templateModel,
                    sourceMaterial,
                    tint),
                new PatinaItemModel(
                    itemDelegate(itemModels, modelSource, data.form(), template, fallbackItem),
                    fallbackItem,
                    data.form(),
                    sourceMaterial,
                    tint)));

            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                blockModels.put(state, models.block());
            }
            itemModels.put(BuiltInRegistries.BLOCK.getKey(block), models.item());
        }
    }

    private static ItemModel itemDelegate(Map<Identifier, ItemModel> itemModels, Block source, VariantForm form, Block template, ItemModel fallback) {
        Item item = form == VariantForm.FULL ? source.asItem() : template.asItem();
        if (item == Items.AIR) item = template.asItem();
        if (item == Items.AIR) item = Items.STONE;
        return itemModels.getOrDefault(BuiltInRegistries.ITEM.getKey(item), fallback);
    }

    private record ModelKey(Block source, VariantForm form, int tint) {}

    private record GeneratedModels(PatinaBlockStateModel block, PatinaItemModel item) {}

}