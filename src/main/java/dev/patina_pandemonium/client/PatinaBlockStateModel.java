package dev.patina_pandemonium.client;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** Selects source appearance from block-entity model data without multiplying registry entries. */
@SuppressWarnings("deprecation")
public class PatinaBlockStateModel extends DelegateBlockStateModel {

    private static final Map<CacheKey, BlockStateModelPart> PART_CACHE = new LinkedHashMap<>(128, 0.75F, true);
    private static final ThreadLocal<List<BlockStateModelPart>> COLLECTED_PARTS = ThreadLocal.withInitial(ArrayList::new);

    private final Map<BlockState, BlockStateModel> models;
    private final Block template;
    private final VariantForm form;

    public PatinaBlockStateModel(Map<BlockState, BlockStateModel> models, Block template, VariantForm form, BlockStateModel delegate) {
        super(delegate);
        this.models = models;
        this.template = template;
        this.form = form;
    }

    public static void clearCache() {
        synchronized (PART_CACHE) {
            PART_CACHE.clear();
        }
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        this.collectParts(VariantData.defaultFor(this.form), Blocks.AIR.defaultBlockState(), null, null, random, parts);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        VariantData data = level.getModelData(pos).get(PatinaVariantBlockEntity.MODEL_DATA);
        this.collectParts(data == null ? VariantData.defaultFor(this.form) : data.normalized(this.form), state, level, pos, random, parts);
    }

    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return this.context(VariantData.defaultFor(this.form), Blocks.AIR.defaultBlockState(), null, null).sourceMaterial();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        VariantData data = level.getModelData(pos).get(PatinaVariantBlockEntity.MODEL_DATA);
        return this.context(data == null ? VariantData.defaultFor(this.form) : data.normalized(this.form), state, level, pos).sourceMaterial();
    }

    private void collectParts(VariantData data, BlockState carrierState,
                              @Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                              RandomSource random, List<BlockStateModelPart> output) {
        RenderContext context = this.context(data, carrierState, level, pos);
        BlockState renderState = this.form == VariantForm.FULL ? context.sourceState() : this.template.withPropertiesOf(carrierState);
        BlockStateModel model = this.models.getOrDefault(renderState, this.delegate);
        List<BlockStateModelPart> collected = COLLECTED_PARTS.get();
        collected.clear();
        if (level == null || pos == null || this.form == VariantForm.FULL) model.collectParts(random, collected);
        else model.collectParts(level, pos, renderState, random, collected);
        for (BlockStateModelPart part : collected) output.add(this.transform(context, part));
        collected.clear();
    }


    private RenderContext context(VariantData data, BlockState carrierState, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = Blocks.STONE;
        BlockState sourceState = source.withPropertiesOf(carrierState);
        BlockStateModel sourceModel = this.models.getOrDefault(sourceState, this.models.get(Blocks.STONE.defaultBlockState()));
        Material.Baked material = sourceModel == null ? this.delegate.particleMaterial() : sourceModel.particleMaterial();
        List<Integer> sourceTints = Minecraft.getInstance().getBlockColors().getTintSources(sourceState).stream()
            .map(tint -> level == null || pos == null ? tint.color(sourceState) : tint.colorInWorld(sourceState, level, pos)).toList();
        return new RenderContext(source, sourceState, material, data.tint(), sourceTints);
    }

    private BlockStateModelPart transform(RenderContext context, BlockStateModelPart part) {
        int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCachedModelParts);
        if (maximum == 0) return this.createPart(context, part);
        CacheKey key = new CacheKey(context.source(), this.form, context.variantTint(), context.sourceTints(), part);
        synchronized (PART_CACHE) {
            BlockStateModelPart cached = PART_CACHE.get(key);
            if (cached != null) return cached;
        }

        BlockStateModelPart transformed = this.createPart(context, part);
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

    private BlockStateModelPart createPart(RenderContext context, BlockStateModelPart part) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : part.getQuads(direction)) {
                builder.addCulledFace(direction, this.transform(context, quad));
            }
        }

        for (BakedQuad quad : part.getQuads(null)) builder.addUnculledFace(this.transform(context, quad));
        return new SimpleModelWrapper(builder.build(), part.ambientOcclusion().isTrue(), context.sourceMaterial());
    }

    private BakedQuad transform(RenderContext context, BakedQuad quad) {
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        if (this.form != VariantForm.FULL) {
            mutable.setSpriteAndMoveUv(context.sourceMaterial());
        }

        int tintIndex = quad.materialInfo().tintIndex();
        int sourceTint = this.form == VariantForm.FULL
            ? tint(context.sourceTints(), tintIndex)
            : tint(context.sourceTints(), context.sourceTints().isEmpty() ? -1 : 0);
        int combinedTint = multiply(context.variantTint(), sourceTint);
        for (int vertex = 0; vertex < 4; vertex++) {
            mutable.setColor(vertex, multiply(mutable.color(vertex), combinedTint));
        }

        mutable.setTintIndex(-1);
        return mutable.toBakedQuad();
    }

    private static int tint(List<Integer> tints, int index) {
        return index < 0 || index >= tints.size() ? 0xFFFFFFFF : tints.get(index);
    }

    private static int multiply(int color, int tint) {
        int alpha = color >>> 24;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private record RenderContext(Block source, BlockState sourceState, Material.Baked sourceMaterial, int variantTint, List<Integer> sourceTints) {}

    private record CacheKey(Block source, VariantForm form, int tint, List<Integer> sourceTints, BlockStateModelPart part) {}

}