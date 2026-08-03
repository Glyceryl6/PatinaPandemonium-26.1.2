package dev.patinapandemonium.registry;

import dev.patinapandemonium.block.PatinaOxidizable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public class VariantRuntime {

    private static final OxidationStage[] STAGES = OxidationStage.values();
    private static final Map<Block, Block> NEXT = new IdentityHashMap<>();
    private static final Map<Block, Block> PREVIOUS = new IdentityHashMap<>();
    private static final Map<Block, Block> WAXED = new IdentityHashMap<>();
    private static final Map<Block, Block> UNWAXED = new IdentityHashMap<>();
    private static final Map<Block, Block> DROPS = new IdentityHashMap<>();
    private static final Map<Block, Links> GENERATED_LINKS = new IdentityHashMap<>();

    public static void linkOxidation(Block from, Block to) {
        if (!(from instanceof PatinaOxidizable)) NEXT.put(from, to);
        if (!(to instanceof PatinaOxidizable)) PREVIOUS.put(to, from);
    }

    public static void linkWaxing(Block unwaxed, Block waxed) {
        if (!(unwaxed instanceof PatinaOxidizable)) WAXED.put(unwaxed, waxed);
        if (!(waxed instanceof PatinaOxidizable)) UNWAXED.put(waxed, unwaxed);
    }

    public static void linkDrop(Block block, Block drop) {
        DROPS.put(block, drop);
    }

    public static Optional<BlockState> next(BlockState state) {
        if (state.getBlock() instanceof PatinaOxidizable oxidizable) return transform(links(state.getBlock(), oxidizable.patinaData()).next(), state);
        return transform(NEXT.get(state.getBlock()), state);
    }

    public static Optional<BlockState> previous(BlockState state) {
        if (state.getBlock() instanceof PatinaOxidizable oxidizable) return transform(links(state.getBlock(), oxidizable.patinaData()).previous(), state);
        return transform(PREVIOUS.get(state.getBlock()), state);
    }

    public static Optional<BlockState> waxed(BlockState state) {
        if (state.getBlock() instanceof PatinaOxidizable oxidizable) return transform(links(state.getBlock(), oxidizable.patinaData()).waxed(), state);
        return transform(WAXED.get(state.getBlock()), state);
    }

    public static Optional<BlockState> unwaxed(BlockState state) {
        if (state.getBlock() instanceof PatinaOxidizable oxidizable) return transform(links(state.getBlock(), oxidizable.patinaData()).unwaxed(), state);
        return transform(UNWAXED.get(state.getBlock()), state);
    }

    public static Block drop(Block block) {
        return DROPS.getOrDefault(block, block);
    }

    private static Links links(Block block, VariantData data) {
        Links links = GENERATED_LINKS.get(block);
        if (links != null) return links;
        OxidationStage nextStage = data.stage().next();
        int previousIndex = data.stage().ordinal() - 1;
        Block next = data.waxed() || nextStage == null ? null : DynamicVariantRegistry.resolveBlock(
            new VariantData(data.sourceId(), nextStage, false, data.form(), data.dyeColor()));
        Block previous = previousIndex < 0 ? null : DynamicVariantRegistry.resolveBlock(
            new VariantData(data.sourceId(), STAGES[previousIndex], data.waxed(), data.form(), data.dyeColor()));
        Block waxed = data.waxed() ? null : DynamicVariantRegistry.resolveBlock(
            new VariantData(data.sourceId(), data.stage(), true, data.form(), data.dyeColor()));
        Block unwaxed = data.waxed() ? DynamicVariantRegistry.resolveBlock(
            new VariantData(data.sourceId(), data.stage(), false, data.form(), data.dyeColor())) : null;
        links = new Links(next, previous, waxed, unwaxed);
        GENERATED_LINKS.put(block, links);
        return links;
    }

    private static Optional<BlockState> transform(@Nullable Block target, BlockState state) {
        return target == null ? Optional.empty() : Optional.of(target.withPropertiesOf(state));
    }

    private record Links(@Nullable Block next, @Nullable Block previous, @Nullable Block waxed, @Nullable Block unwaxed) {}

}