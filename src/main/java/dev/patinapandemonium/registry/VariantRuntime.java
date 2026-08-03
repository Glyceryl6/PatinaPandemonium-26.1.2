package dev.patinapandemonium.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public class VariantRuntime {

    private static final Map<Block, Block> NEXT = new IdentityHashMap<>();
    private static final Map<Block, Block> PREVIOUS = new IdentityHashMap<>();
    private static final Map<Block, Block> WAXED = new IdentityHashMap<>();
    private static final Map<Block, Block> UNWAXED = new IdentityHashMap<>();
    private static final Map<Block, Block> DROPS = new IdentityHashMap<>();

    public static void linkOxidation(Block from, Block to) {
        NEXT.put(from, to);
        PREVIOUS.put(to, from);
    }

    public static void linkWaxing(Block unwaxed, Block waxed) {
        WAXED.put(unwaxed, waxed);
        UNWAXED.put(waxed, unwaxed);
    }

    public static void linkDrop(Block block, Block drop) {
        DROPS.put(block, drop);
    }

    public static Optional<BlockState> next(BlockState state) {
        return transform(NEXT, state);
    }

    public static Optional<BlockState> previous(BlockState state) {
        return transform(PREVIOUS, state);
    }

    public static Optional<BlockState> waxed(BlockState state) {
        return transform(WAXED, state);
    }

    public static Optional<BlockState> unwaxed(BlockState state) {
        return transform(UNWAXED, state);
    }

    public static Block drop(Block block) {
        return DROPS.getOrDefault(block, block);
    }

    private static Optional<BlockState> transform(Map<Block, Block> transformations, BlockState state) {
        Block target = transformations.get(state.getBlock());
        return target == null ? Optional.empty() : Optional.of(target.withPropertiesOf(state));
    }

}