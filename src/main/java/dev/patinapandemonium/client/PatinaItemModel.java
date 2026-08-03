package dev.patinapandemonium.client;

import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Resolves the logical variant from the stack component and reuses already baked models.
 */
public class PatinaItemModel implements ItemModel {

    private static final Map<CacheKey, BakedQuad> QUAD_CACHE = new LinkedHashMap<>(256, 0.75F, true);

    private final Map<BlockState, BlockStateModel> blockModels;
    private final Map<Identifier, ItemModel> itemModels;
    private final Block template;
    private final VariantForm form;
    private final ItemModel fallback;
    private final BlockStateModel fallbackBlock;

    public PatinaItemModel(Map<BlockState, BlockStateModel> blockModels, Map<Identifier, ItemModel> itemModels,
                           Block template, VariantForm form, ItemModel fallback, BlockStateModel fallbackBlock) {
        this.blockModels = blockModels;
        this.itemModels = itemModels;
        this.template = template;
        this.form = form;
        this.fallback = fallback;
        this.fallbackBlock = fallbackBlock;
    }

    public static void clearCache() {
        synchronized (QUAD_CACHE) {
            QUAD_CACHE.clear();
        }
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        RenderContext context = this.context(DynamicVariantRegistry.data(stack, this.form));
        ItemModel delegate = this.itemDelegate(context.source());
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(context);
        TransformingRenderState transformed = new TransformingRenderState(renderState, context);
        delegate.update(transformed, stack, resolver, displayContext, level, owner, seed);
        if (transformed.usesSpecialModel() && delegate != this.fallback) {
            renderState.clear();
            renderState.appendModelIdentityElement(this);
            renderState.appendModelIdentityElement(context);
            this.fallback.update(new TransformingRenderState(renderState, context), stack, resolver, displayContext, level, owner, seed);
        }
    }

    private RenderContext context(VariantData data) {
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = Blocks.STONE;
        BlockStateModel sourceModel = this.blockModels.getOrDefault(source.defaultBlockState(), this.fallbackBlock);
        return new RenderContext(source, sourceModel.particleMaterial(), data.tint());
    }

    private ItemModel itemDelegate(Block source) {
        Item item = this.form == VariantForm.FULL ? source.asItem() : this.template.asItem();
        if (item == Items.AIR) item = this.template.asItem();
        if (item == Items.AIR) item = Items.STONE;
        return this.itemModels.getOrDefault(BuiltInRegistries.ITEM.getKey(item), this.fallback);
    }

    private BakedQuad transform(RenderContext context, BakedQuad quad) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCachedItemQuads);
        if (maximum == 0) return this.createQuad(context, quad);
        CacheKey key = new CacheKey(context.source(), this.form, context.tint(), quad);
        synchronized (QUAD_CACHE) {
            BakedQuad cached = QUAD_CACHE.get(key);
            if (cached != null) return cached;
        }
        BakedQuad transformed = this.createQuad(context, quad);
        synchronized (QUAD_CACHE) {
            QUAD_CACHE.put(key, transformed);
            Iterator<CacheKey> iterator = QUAD_CACHE.keySet().iterator();
            while (QUAD_CACHE.size() > maximum && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        return transformed;
    }

    private BakedQuad createQuad(RenderContext context, BakedQuad quad) {
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        if (this.form != VariantForm.FULL) mutable.setSpriteAndMoveUv(context.sourceMaterial());
        for (int vertex = 0; vertex < 4; vertex++)
            mutable.setColor(vertex, multiply(mutable.color(vertex), context.tint()));
        return mutable.toBakedQuad();
    }

    private static int multiply(int color, int tint) {
        int alpha = color >>> 24;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private class TransformingRenderState extends ItemStackRenderState {

        private final ItemStackRenderState output;
        private final RenderContext context;
        private boolean specialModel;

        private TransformingRenderState(ItemStackRenderState output, RenderContext context) {
            this.output = output;
            this.context = context;
        }

        private boolean usesSpecialModel() {
            return this.specialModel;
        }

        @Override
        public void ensureCapacity(int requestedCount) {
            this.output.ensureCapacity(requestedCount);
        }

        @Override
        public LayerRenderState newLayer() {
            return new TransformingLayerRenderState(this, this.output.newLayer(), this.context);
        }

        @Override
        public void clear() {
            this.output.clear();
        }

        @Override
        public void setAnimated() {
            this.output.setAnimated();
        }

        @Override
        public void appendModelIdentityElement(Object element) {
            this.output.appendModelIdentityElement(element);
        }

        @Override
        public boolean isAnimated() {
            return this.output.isAnimated();
        }

        @Override
        public boolean isEmpty() {
            return this.output.isEmpty();
        }

        @Override
        public boolean usesBlockLight() {
            return this.output.usesBlockLight();
        }

        @Override
        public Material.@Nullable Baked pickParticleMaterial(RandomSource random) {
            return this.output.pickParticleMaterial(random);
        }

        @Override
        public void visitExtents(Consumer<Vector3fc> output) {
            this.output.visitExtents(output);
        }

        @Override
        public AABB getModelBoundingBox() {
            return this.output.getModelBoundingBox();
        }

        @Override
        public void setOversizedInGui(boolean oversizedInGui) {
            this.output.setOversizedInGui(oversizedInGui);
        }

        @Override
        public boolean isOversizedInGui() {
            return this.output.isOversizedInGui();
        }

    }

    private class TransformingLayerRenderState extends ItemStackRenderState.LayerRenderState {

        private final TransformingRenderState owner;
        private final ItemStackRenderState.LayerRenderState output;
        private final RenderContext context;

        private TransformingLayerRenderState(TransformingRenderState owner, ItemStackRenderState.LayerRenderState output,
                                             RenderContext context) {
            owner.super();
            this.owner = owner;
            this.output = output;
            this.context = context;
        }

        @Override
        public void clear() {
            this.output.clear();
        }

        @Override
        public List<BakedQuad> prepareQuadList() {
            return new TransformingQuadList(this.output.prepareQuadList(), this.context);
        }

        @Override
        public void setUsesBlockLight(boolean usesBlockLight) {
            this.output.setUsesBlockLight(usesBlockLight);
        }

        @Override
        public void setExtents(Supplier<Vector3fc[]> extents) {
            this.output.setExtents(extents);
        }

        @Override
        public void setParticleMaterial(Material.Baked particleMaterial) {
            this.output.setParticleMaterial(this.context.sourceMaterial());
        }

        @Override
        public void setItemTransform(ItemTransform transform) {
            this.output.setItemTransform(transform);
        }

        @Override
        public void setLocalTransform(Matrix4fc transform) {
            this.output.setLocalTransform(transform);
        }

        @Override
        public <T> void setupSpecialModel(SpecialModelRenderer<T> renderer, @Nullable T argument) {
            this.owner.specialModel = true;
            this.output.setupSpecialModel(renderer, argument);
        }

        @Override
        public void setFoilType(ItemStackRenderState.FoilType foilType) {
            this.output.setFoilType(foilType);
        }

        @Override
        public IntList tintLayers() {
            return this.output.tintLayers();
        }

    }

    private class TransformingQuadList extends AbstractList<BakedQuad> {

        private final List<BakedQuad> output;
        private final RenderContext context;

        private TransformingQuadList(List<BakedQuad> output, RenderContext context) {
            this.output = output;
            this.context = context;
        }

        @Override
        public BakedQuad get(int index) {
            return this.output.get(index);
        }

        @Override
        public int size() {
            return this.output.size();
        }

        @Override
        public BakedQuad set(int index, BakedQuad element) {
            return this.output.set(index, PatinaItemModel.this.transform(this.context, element));
        }

        @Override
        public void add(int index, BakedQuad element) {
            this.output.add(index, PatinaItemModel.this.transform(this.context, element));
        }

        @Override
        public boolean addAll(Collection<? extends BakedQuad> collection) {
            boolean changed = false;
            for (BakedQuad quad : collection)
                changed |= this.output.add(PatinaItemModel.this.transform(this.context, quad));
            return changed;
        }

        @Override
        public boolean addAll(int index, Collection<? extends BakedQuad> collection) {
            int insertion = index;
            for (BakedQuad quad : collection)
                this.output.add(insertion++, PatinaItemModel.this.transform(this.context, quad));
            return insertion != index;
        }

        @Override
        public BakedQuad remove(int index) {
            return this.output.remove(index);
        }

        @Override
        public void clear() {
            this.output.clear();
        }

    }

    private record RenderContext(Block source, Material.Baked sourceMaterial, int tint) {
    }

    private record CacheKey(Block source, VariantForm form, int tint, BakedQuad quad) {
    }

}