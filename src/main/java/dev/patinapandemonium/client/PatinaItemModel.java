package dev.patinapandemonium.client;

import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.registry.VariantForm;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Delegates item transforms and effects while lazily recoloring and retexturing submitted quads. */
public class PatinaItemModel implements ItemModel {

    private static final Map<CacheKey, BakedQuad> QUAD_CACHE = new LinkedHashMap<>(256, 0.75F, true);

    private final ItemModel delegate;
    private final ItemModel fallback;
    private final VariantForm form;
    private final Material.Baked sourceMaterial;
    private final int tint;

    public PatinaItemModel(ItemModel delegate, ItemModel fallback, VariantForm form, Material.Baked sourceMaterial, int tint) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.form = form;
        this.sourceMaterial = sourceMaterial;
        this.tint = tint;
    }

    public static void clearCache() {
        synchronized (QUAD_CACHE) {
            QUAD_CACHE.clear();
        }
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        renderState.appendModelIdentityElement(this);
        TransformingRenderState transformed = new TransformingRenderState(renderState);
        this.delegate.update(transformed, stack, resolver, displayContext, level, owner, seed);
        if (transformed.usesSpecialModel() && this.delegate != this.fallback) {
            renderState.clear();
            renderState.appendModelIdentityElement(this);
            this.fallback.update(new TransformingRenderState(renderState), stack, resolver, displayContext, level, owner, seed);
        }
    }

    private BakedQuad transform(BakedQuad quad) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCachedItemQuads);
        if (maximum == 0) return this.createQuad(quad);
        CacheKey key = new CacheKey(this, quad);
        synchronized (QUAD_CACHE) {
            BakedQuad cached = QUAD_CACHE.get(key);
            if (cached != null) return cached;
        }

        BakedQuad transformed = this.createQuad(quad);
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

    private BakedQuad createQuad(BakedQuad quad) {
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        if (this.form != VariantForm.FULL) mutable.setSpriteAndMoveUv(this.sourceMaterial);
        for (int vertex = 0; vertex < 4; vertex++) {
            mutable.setColor(vertex, multiply(mutable.color(vertex), this.tint));
        }
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
        private boolean specialModel;

        private TransformingRenderState(ItemStackRenderState output) {
            this.output = output;
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
            return new TransformingLayerRenderState(this, this.output.newLayer());
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

        private TransformingLayerRenderState(TransformingRenderState owner, ItemStackRenderState.LayerRenderState output) {
            owner.super();
            this.owner = owner;
            this.output = output;
        }

        @Override
        public void clear() {
            this.output.clear();
        }

        @Override
        public List<BakedQuad> prepareQuadList() {
            return new TransformingQuadList(this.output.prepareQuadList());
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
            this.output.setParticleMaterial(PatinaItemModel.this.sourceMaterial);
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

        private TransformingQuadList(List<BakedQuad> output) {
            this.output = output;
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
            return this.output.set(index, PatinaItemModel.this.transform(element));
        }

        @Override
        public void add(int index, BakedQuad element) {
            this.output.add(index, PatinaItemModel.this.transform(element));
        }

        @Override
        public boolean addAll(Collection<? extends BakedQuad> collection) {
            boolean changed = false;
            for (BakedQuad quad : collection) changed |= this.output.add(PatinaItemModel.this.transform(quad));
            return changed;
        }

        @Override
        public boolean addAll(int index, Collection<? extends BakedQuad> collection) {
            int insertion = index;
            for (BakedQuad quad : collection) this.output.add(insertion++, PatinaItemModel.this.transform(quad));
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

    private record CacheKey(PatinaItemModel owner, BakedQuad quad) {}

}