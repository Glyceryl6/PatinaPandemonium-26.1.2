package dev.patina_pandemonium.client;

import com.google.common.reflect.TypeToken;
import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Replaces carrier descriptors with shared model wrappers and exposes synchronized entity tint data to vanilla renderers. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class PatinaClient {

    private static final ContextKey<Integer> ENTITY_TINT = new ContextKey<>(PatinaPandemonium.id("entity_tint"));
    private static final ContextKey<Integer> FIRE_TINT = new ContextKey<>(PatinaPandemonium.id("fire_tint"));
    private static final ContextKey<Integer> PROJECTILE_TINT = new ContextKey<>(PatinaPandemonium.id("projectile_tint"));
    private static final ThreadLocal<ArrayDeque<Integer>> MODEL_TINTS = new ThreadLocal<>();
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
            AttachmentType<ItemVariantData> entityType = DynamicVariantRegistry.ENTITY_VARIANT_DATA.get();
            AttachmentType<ItemVariantData> fireType = DynamicVariantRegistry.ENTITY_FIRE_VARIANT_DATA.get();
            state.setRenderData(ENTITY_TINT, entity.hasData(entityType) ? entity.getData(entityType).tint() : null);
            state.setRenderData(FIRE_TINT, entity.hasData(fireType) ? entity.getData(fireType).tint() : null);
            state.setRenderData(PROJECTILE_TINT, entity instanceof Projectile && entity.hasData(entityType) ? entity.getData(entityType).tint() : null);
        });
    }

    public static int entityTint(EntityRenderState state) {
        Integer tint = state.getRenderData(ENTITY_TINT);
        return tint == null ? -1 : tint;
    }

    public static int fireTint(EntityRenderState state) {
        Integer tint = state.getRenderData(FIRE_TINT);
        return tint == null ? -1 : tint;
    }

    public static int projectileTint(EntityRenderState state) {
        Integer tint = state.getRenderData(PROJECTILE_TINT);
        return tint == null ? -1 : tint;
    }

    public static void beginModelTint(int tint) {
        ArrayDeque<Integer> tints = MODEL_TINTS.get();
        if (tints == null) {
            tints = new ArrayDeque<>();
            MODEL_TINTS.set(tints);
        }
        tints.push(tint);
    }

    public static void endModelTint() {
        ArrayDeque<Integer> tints = MODEL_TINTS.get();
        if (tints == null) return;
        if (!tints.isEmpty()) tints.pop();
        if (tints.isEmpty()) MODEL_TINTS.remove();
    }

    public static int applyModelTint(int color) {
        ArrayDeque<Integer> tints = MODEL_TINTS.get();
        return tints == null || tints.isEmpty() || tints.peek() == -1 ? color : multiply(color, tints.peek());
    }

    public static int[] applyModelTints(int[] colors) {
        ArrayDeque<Integer> tints = MODEL_TINTS.get();
        if (tints == null || tints.isEmpty() || tints.peek() == -1) return colors;
        int[] tinted = colors.clone();
        for (int index = 0; index < tinted.length; index++) tinted[index] = multiply(tinted[index], tints.peek());
        return tinted;
    }

    public static void applyBlockModelTint(BlockModelRenderState renderState, int tint) {
        if (tint == -1) return;
        List<BlockStateModelPart> parts = renderState.modelParts;
        if (parts == null) return;
        parts.replaceAll(part -> tintedPart(part, tint));
    }

    private static BlockStateModelPart tintedPart(BlockStateModelPart part, int tint) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : part.getQuads(direction)) builder.addCulledFace(direction, tintedQuad(quad, tint));
        }
        for (BakedQuad quad : part.getQuads(null)) builder.addUnculledFace(tintedQuad(quad, tint));
        return new SimpleModelWrapper(builder.build(), part.ambientOcclusion().isTrue(), part.particleMaterial());
    }

    private static BakedQuad tintedQuad(BakedQuad quad, int tint) {
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        for (int vertex = 0; vertex < 4; vertex++) mutable.setColor(vertex, multiply(mutable.color(vertex), tint));
        return mutable.toBakedQuad();
    }

    private static int multiply(int color, int tint) {
        int alpha = (color >>> 24) * (tint >>> 24) / 0xFF;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelBakery.BakingResult result = event.getBakingResult();
        Map<BlockState, BlockStateModel> blockModels = result.blockStateModels();
        Map<Identifier, ItemModel> itemModels = result.itemStackModels();
        Map<BlockState, BlockStateModel> originalBlockModels = new HashMap<>(blockModels);
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
            sharedBlockModels.put(form, new PatinaBlockStateModel(originalBlockModels, template, form, templateModel));
            sharedItemModels.put(form, new PatinaItemModel(originalBlockModels, originalItemModels, template, form, fallbackItem, fallbackBlock));
        }

        for (Block block : DynamicVariantRegistry.generated()) {
            if (!(block instanceof PatinaOxidizable oxidizable)) continue;
            VariantForm form = oxidizable.patinaForm();
            BlockStateModel blockModel = sharedBlockModels.get(form);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) blockModels.put(state, blockModel);
            Item item = block.asItem();
            if (item != Items.AIR) itemModels.put(BuiltInRegistries.ITEM.getKey(item), sharedItemModels.get(form));
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            if (!DynamicVariantRegistry.isNativeBlockEntitySource(block)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockStateModel delegate = originalBlockModels.get(state);
                if (delegate != null) blockModels.put(state, new PatinaBlockStateModel(originalBlockModels, block, delegate));
            }
        }

        itemModels.put(DynamicVariantRegistry.VARIANT_ITEM_MODEL, new PatinaItemModel(originalItemModels, fallbackItem, fallbackBlock));
    }

}