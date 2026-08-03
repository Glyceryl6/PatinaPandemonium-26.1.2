package dev.patinapandemonium.client;

import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses already baked source/template geometry and applies oxidation tint without new textures. */
public class PatinaBlockStateModel extends DelegateBlockStateModel {

    private static final Map<CacheKey, BlockStateModelPart> PART_CACHE = new LinkedHashMap<>(128, 0.75F, true);
    private static final ThreadLocal<List<BlockStateModelPart>> COLLECTED_PARTS = ThreadLocal.withInitial(ArrayList::new);

    private final Map<BlockState, BlockStateModel> models;
    private final Block source;
    private final Block template;
    private final VariantForm form;
    private final Material.Baked sourceMaterial;
    private final int tint;

    public PatinaBlockStateModel(Map<BlockState, BlockStateModel> models, Block source, Block template,
                                 VariantForm form, BlockStateModel delegate, Material.Baked sourceMaterial, int tint) {
        super(delegate);
        this.models = models;
        this.source = source;
        this.template = template;
        this.form = form;
        this.sourceMaterial = sourceMaterial;
        this.tint = 0xFF000000 | tint;
    }

    public static void clearCache() {
        synchronized (PART_CACHE) {
            PART_CACHE.clear();
        }
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        this.collectParts(this.source.defaultBlockState(), random, parts);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        BlockState renderState = this.renderState(state);
        BlockStateModel model = this.models.getOrDefault(renderState, this.delegate);
        List<BlockStateModelPart> collected = COLLECTED_PARTS.get();
        collected.clear();
        model.collectParts(level, pos, renderState, random, collected);
        this.addTransformed(collected, parts);
    }

    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return this.sourceMaterial;
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return this.sourceMaterial;
    }

    private void collectParts(BlockState state, RandomSource random, List<BlockStateModelPart> output) {
        BlockState renderState = this.renderState(state);
        BlockStateModel model = this.models.getOrDefault(renderState, this.delegate);
        List<BlockStateModelPart> collected = COLLECTED_PARTS.get();
        collected.clear();
        model.collectParts(random, collected);
        this.addTransformed(collected, output);
    }

    private BlockState renderState(BlockState state) {
        return this.form == VariantForm.FULL
            ? this.source.defaultBlockState()
            : this.template.withPropertiesOf(state);
    }

    private void addTransformed(List<BlockStateModelPart> collected, List<BlockStateModelPart> output) {
        for (BlockStateModelPart part : collected) {
            output.add(this.transform(part));
        }
        collected.clear();
    }

    private BlockStateModelPart transform(BlockStateModelPart part) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCachedModelParts);
        if (maximum == 0) {
            return this.createPart(part);
        }

        CacheKey key = new CacheKey(this, part);
        synchronized (PART_CACHE) {
            BlockStateModelPart cached = PART_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }

        BlockStateModelPart transformed = this.createPart(part);
        synchronized (PART_CACHE) {
            PART_CACHE.put(key, transformed);
            Iterator<CacheKey> iterator = PART_CACHE.keySet().iterator();
            while (PART_CACHE.size() > maximum && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        return transformed;
    }

    private BlockStateModelPart createPart(BlockStateModelPart part) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : part.getQuads(direction)) {
                builder.addCulledFace(direction, this.transform(quad));
            }
        }

        for (BakedQuad quad : part.getQuads(null)) {
            builder.addUnculledFace(this.transform(quad));
        }

        return new SimpleModelWrapper(builder.build(), part.ambientOcclusion().isTrue(), this.sourceMaterial);
    }

    private BakedQuad transform(BakedQuad quad) {
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        if (this.form != VariantForm.FULL) {
            mutable.setSpriteAndMoveUv(this.sourceMaterial);
        }
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

    private record CacheKey(PatinaBlockStateModel owner, BlockStateModelPart part) {}
}
