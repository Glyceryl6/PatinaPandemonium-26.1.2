package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.DynamicWoodTypes;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;

public class GeneratedBlockFactory {

    private static final Set<VariantForm> NO_COLLISION_FORMS = Set.of(
        VariantForm.BUTTON,
        VariantForm.PRESSURE_PLATE,
        VariantForm.SIGN,
        VariantForm.WALL_SIGN);
    private static final Map<VariantForm, BlockCreator> CREATORS = Map.of(
        VariantForm.FULL, (_, properties, data) -> new PatinaBlock(properties, data),
        VariantForm.SLAB, (_, properties, data) -> new PatinaSlabBlock(properties, data),
        VariantForm.STAIRS, (base, properties, data) -> new PatinaStairBlock(base.defaultBlockState(), properties, data),
        VariantForm.WALL, (_, properties, data) -> new PatinaWallBlock(properties, data),
        VariantForm.FENCE, (_, properties, data) -> new PatinaFenceBlock(properties, data),
        VariantForm.FENCE_GATE, (_, properties, data) -> new PatinaFenceGateBlock(properties, data),
        VariantForm.BUTTON, (_, properties, data) -> new PatinaButtonBlock(properties, data),
        VariantForm.PRESSURE_PLATE, (_, properties, data) -> new PatinaPressurePlateBlock(properties, data),
        VariantForm.SIGN, (_, properties, data) -> new PatinaStandingSignBlock(
            DynamicWoodTypes.getOrCreate(data), properties, data),
        VariantForm.WALL_SIGN, (_, properties, data) -> new PatinaWallSignBlock(
            DynamicWoodTypes.getOrCreate(data), properties, data));

    public static Block create(Identifier id, Block source, Block stageBase, VariantData data) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockState sourceState = source.defaultBlockState();
        int lightEmission = sourceState.getLightEmission();
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofLegacyCopy(source)
            .mapColor(sourceState.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            .lightLevel(ignored -> lightEmission)
            .emissiveRendering((_, _, _) -> false)
            .offsetType(BlockBehaviour.OffsetType.NONE)
            .setId(key).randomTicks();
        if (NO_COLLISION_FORMS.contains(data.form())) {
            properties.noCollision().noOcclusion();
        }

        return CREATORS.get(data.form()).create(stageBase, properties, data);
    }

    @FunctionalInterface
    private interface BlockCreator {
        Block create(Block stageBase, BlockBehaviour.Properties properties, VariantData data);
    }

}