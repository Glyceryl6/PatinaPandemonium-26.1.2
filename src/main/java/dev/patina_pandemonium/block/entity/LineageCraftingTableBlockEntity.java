package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Dedicated block entity for crafted Patina crafting tables whose own lineage participates in later crafts. */
public class LineageCraftingTableBlockEntity extends PatinaVariantBlockEntity {

    public LineageCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(DynamicVariantRegistry.LINEAGE_CRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state);
    }

}