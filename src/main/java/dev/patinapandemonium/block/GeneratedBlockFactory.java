package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.DynamicWoodTypes;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class GeneratedBlockFactory {

    public static Block create(Identifier id, Block source, Block stageBase, VariantData data) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(source).setId(key).randomTicks();
        if (data.form() == VariantForm.BUTTON
                || data.form() == VariantForm.PRESSURE_PLATE
                || data.form() == VariantForm.SIGN
                || data.form() == VariantForm.WALL_SIGN) {
            properties.noCollision().noOcclusion();
        }

        return switch (data.form()) {
            case FULL -> new PatinaBlock(properties, data);
            case SLAB -> new PatinaSlabBlock(properties, data);
            case STAIRS -> new PatinaStairBlock(stageBase.defaultBlockState(), properties, data);
            case WALL -> new PatinaWallBlock(properties, data);
            case FENCE -> new PatinaFenceBlock(properties, data);
            case FENCE_GATE -> new PatinaFenceGateBlock(properties, data);
            case BUTTON -> new PatinaButtonBlock(properties, data);
            case PRESSURE_PLATE -> new PatinaPressurePlateBlock(properties, data);
            case SIGN -> new PatinaStandingSignBlock(DynamicWoodTypes.getOrCreate(data), properties, data);
            case WALL_SIGN -> new PatinaWallSignBlock(DynamicWoodTypes.getOrCreate(data), properties, data);
        };
    }

}