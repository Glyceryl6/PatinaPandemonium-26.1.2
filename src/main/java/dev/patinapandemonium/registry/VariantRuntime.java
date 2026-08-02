package dev.patinapandemonium.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public class VariantRuntime {

    private static final Map<Block, Block> NEXT = new IdentityHashMap<>();

    public static void link(Block from, Block to) {
        NEXT.put(from, to);
    }

    public static Optional<BlockState> next(BlockState state) {
        Block next = NEXT.get(state.getBlock());
        return next == null ? Optional.empty() : Optional.of(next.defaultBlockState().getBlock().withPropertiesOf(state));
    }

}
