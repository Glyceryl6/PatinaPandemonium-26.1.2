package dev.patinapandemonium.client;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.OxidationStage;
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

/** Installs flyweight baked models after the vanilla model registry has been created. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class PatinaClient {

    private static final Map<VariantForm, Block> TEMPLATES = Map.of(
        VariantForm.FULL, Blocks.STONE,
        VariantForm.SLAB, Blocks.STONE_SLAB,
        VariantForm.STAIRS, Blocks.STONE_STAIRS,
        VariantForm.WALL, Blocks.COBBLESTONE_WALL,
        VariantForm.FENCE, Blocks.OAK_FENCE,
        VariantForm.FENCE_GATE, Blocks.OAK_FENCE_GATE,
        VariantForm.BUTTON, Blocks.STONE_BUTTON,
        VariantForm.PRESSURE_PLATE, Blocks.STONE_PRESSURE_PLATE);

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelBakery.BakingResult result = event.getBakingResult();
        Map<BlockState, BlockStateModel> blockModels = result.blockStateModels();
        Map<Identifier, ItemModel> itemModels = result.itemStackModels();
        Map<ModelKey, PatinaBlockStateModel> generatedModels = new HashMap<>();
        BlockStateModel fallback = blockModels.get(Blocks.STONE.defaultBlockState());
        PatinaBlockStateModel.clearCache();
        if (fallback == null) return;
        for (VariantEntry entry : DynamicVariantRegistry.generated()) {
            Block template = TEMPLATES.get(entry.data().form());
            BlockStateModel sourceModel = blockModels.getOrDefault(entry.source().defaultBlockState(), fallback);
            BlockStateModel templateModel = blockModels.getOrDefault(template.defaultBlockState(), fallback);
            Material.Baked sourceMaterial = sourceModel.particleMaterial();
            ModelKey key = new ModelKey(entry.source(), entry.data().form(), entry.data().stage());
            PatinaBlockStateModel model = generatedModels.computeIfAbsent(key, ignored -> new PatinaBlockStateModel(
                blockModels,
                entry.source(),
                template,
                entry.data().form(),
                entry.data().form() == VariantForm.FULL ? sourceModel : templateModel,
                sourceMaterial,
                entry.data().stage().fallbackColor()
            ));

            for (BlockState state : entry.block().getStateDefinition().getPossibleStates()) {
                blockModels.put(state, model);
            }

            aliasItemModel(itemModels, entry, template);
        }
    }

    private static void aliasItemModel(Map<Identifier, ItemModel> itemModels, VariantEntry entry, Block template) {
        Item sourceItem = entry.source().asItem();
        if (sourceItem == Items.AIR) {
            sourceItem = template.asItem();
        }
        if (sourceItem == Items.AIR) {
            sourceItem = Items.STONE;
        }

        ItemModel sourceModel = itemModels.get(BuiltInRegistries.ITEM.getKey(sourceItem));
        if (sourceModel != null) {
            itemModels.put(entry.blockId(), sourceModel);
        }
    }

    private record ModelKey(Block source, VariantForm form, OxidationStage stage) {}

}