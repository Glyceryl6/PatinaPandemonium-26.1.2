package dev.patina_pandemonium.client;

import com.google.common.reflect.TypeToken;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Replaces carrier descriptors with shared model wrappers and exposes synchronized entity tint data to vanilla renderers. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class PatinaClient {

    private static final ContextKey<Integer> ENTITY_TINT = new ContextKey<>(PatinaPandemonium.id("entity_tint"));
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
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(DynamicVariantRegistry.VARIANT_FABRICATOR_MENU.get(), VariantFabricatorScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
            AttachmentType<ItemVariantData> type = DynamicVariantRegistry.ENTITY_VARIANT_DATA.get();
            state.setRenderData(ENTITY_TINT, entity.hasData(type) ? entity.getData(type).tint() : null);
        });
    }

    public static int entityTint(EntityRenderState state) {
        Integer tint = state.getRenderData(ENTITY_TINT);
        return tint == null ? -1 : tint;
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelBakery.BakingResult result = event.getBakingResult();
        Map<BlockState, BlockStateModel> blockModels = result.blockStateModels();
        Map<Identifier, ItemModel> itemModels = result.itemStackModels();
        Map<Identifier, ItemModel> originalItemModels = new HashMap<>(itemModels);
        BlockStateModel fallbackBlock = blockModels.get(Blocks.STONE.defaultBlockState());
        ItemModel fallbackItem = itemModels.get(BuiltInRegistries.ITEM.getKey(Items.STONE));
        PatinaBlockStateModel.clearCache();
        PatinaItemModel.clearCache();
        if (fallbackBlock == null || fallbackItem == null) return;
        EnumMap<VariantForm, BlockStateModel> sharedBlockModels = new EnumMap<>(VariantForm.class);
        EnumMap<VariantForm, ItemModel> sharedItemModels = new EnumMap<>(VariantForm.class);
        for (VariantForm form : VariantForm.values()) {
            Block template = TEMPLATES.get(form);
            BlockStateModel templateModel = blockModels.getOrDefault(template.defaultBlockState(), fallbackBlock);
            sharedBlockModels.put(form, new PatinaBlockStateModel(blockModels, template, form, templateModel));
            sharedItemModels.put(form, new PatinaItemModel(blockModels, itemModels, template, form, fallbackItem, fallbackBlock));
        }

        for (Block block : DynamicVariantRegistry.generated()) {
            if (!(block instanceof PatinaOxidizable oxidizable)) continue;
            VariantForm form = oxidizable.patinaForm();
            BlockStateModel blockModel = sharedBlockModels.get(form);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) blockModels.put(state, blockModel);
            Item item = block.asItem();
            if (item != Items.AIR) itemModels.put(BuiltInRegistries.ITEM.getKey(item), sharedItemModels.get(form));
        }

        itemModels.put(DynamicVariantRegistry.VARIANT_ITEM_MODEL, new PatinaItemModel(originalItemModels, fallbackItem, fallbackBlock));
    }

}