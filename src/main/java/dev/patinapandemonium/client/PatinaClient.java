package dev.patinapandemonium.client;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;

/**
 * Replaces the nine carrier descriptors with component/model-data aware flyweight models.
 */
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
        BlockStateModel fallbackBlock = blockModels.get(Blocks.STONE.defaultBlockState());
        ItemModel fallbackItem = itemModels.get(BuiltInRegistries.ITEM.getKey(Items.STONE));
        PatinaBlockStateModel.clearCache();
        PatinaItemModel.clearCache();
        if (fallbackBlock == null || fallbackItem == null) return;
        for (Block block : DynamicVariantRegistry.generated()) {
            VariantForm form = ((PatinaOxidizable) block).patinaForm();
            Block template = TEMPLATES.get(form);
            BlockStateModel templateModel = blockModels.getOrDefault(template.defaultBlockState(), fallbackBlock);
            PatinaBlockStateModel blockModel = new PatinaBlockStateModel(blockModels, template, form, templateModel);
            PatinaItemModel itemModel = new PatinaItemModel(blockModels, itemModels, template, form, fallbackItem, fallbackBlock);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) blockModels.put(state, blockModel);
            itemModels.put(BuiltInRegistries.BLOCK.getKey(block), itemModel);
        }
    }

}