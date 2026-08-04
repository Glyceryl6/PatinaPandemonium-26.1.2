package dev.patina_pandemonium.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public record VariantEntry(VariantData data, Identifier blockId, Block block, Block source, boolean generated) {
}