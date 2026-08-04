package dev.patina_pandemonium.block;

import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.Map;
import java.util.Set;

public class GeneratedBlockFactory {

    private static final Set<VariantForm> NO_OCCLUSION_FORMS =
            Set.of(VariantForm.CARPET, VariantForm.BUTTON, VariantForm.PRESSURE_PLATE);
    private static final Set<VariantForm> NO_COLLISION_FORMS =
            Set.of(VariantForm.BUTTON, VariantForm.PRESSURE_PLATE);
    private static final Map<VariantForm, BlockCreator> CREATORS = Map.of(
            VariantForm.SLAB, PatinaSlabBlock::new,
            VariantForm.WALL, PatinaWallBlock::new,
            VariantForm.FENCE, PatinaFenceBlock::new,
            VariantForm.FENCE_GATE, PatinaFenceGateBlock::new,
            VariantForm.CARPET, PatinaCarpetBlock::new,
            VariantForm.BUTTON, PatinaButtonBlock::new,
            VariantForm.PRESSURE_PLATE, PatinaPressurePlateBlock::new);

    public static Block create(Identifier id, VariantForm form) {
        return create(id, form, false);
    }

    public static Block create(Identifier id, VariantForm form, boolean nonOccluding) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE)
                .pushReaction(PushReaction.BLOCK)
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .randomTicks();
        return create(form, Blocks.STONE, properties, nonOccluding);
    }

    public static Block create(Identifier id, VariantForm form, Block source) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(source)
                .mapColor(source.defaultMapColor())
                .lightLevel(_ -> source.defaultBlockState().getLightEmission())
                .isValidSpawn((state, level, pos, _) -> state.isFaceSturdy(level, pos, Direction.UP) && state.getLightEmission(level, pos) < 14)
                .isRedstoneConductor(BlockBehaviour.BlockStateBase::isCollisionShapeFullBlock)
                .isSuffocating((state, level, pos) -> state.blocksMotion() && state.isCollisionShapeFullBlock(level, pos))
                .isViewBlocking((state, level, pos) -> state.blocksMotion() && state.isCollisionShapeFullBlock(level, pos))
                .postProcess((_, _, _) -> null)
                .emissiveRendering((_, _, _) -> false)
                .offsetType(BlockBehaviour.OffsetType.NONE)
                .noLootTable()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .randomTicks();
        return create(form, source, properties, !source.defaultBlockState().canOcclude());
    }

    public static Block createDelegated(Identifier id, Block source) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(source)
                .setId(ResourceKey.create(Registries.BLOCK, id)).randomTicks();
        return PatinaDelegatingBlock.create(source, properties);
    }

    private static Block create(VariantForm form, Block source, BlockBehaviour.Properties properties, boolean nonOccluding) {
        if (NO_COLLISION_FORMS.contains(form)) properties.noCollision();
        if (nonOccluding || NO_OCCLUSION_FORMS.contains(form)) properties.noOcclusion();
        if (form == VariantForm.FULL) return PatinaBlock.create(source, properties);
        return form == VariantForm.STAIRS
                ? new PatinaStairBlock(source.defaultBlockState(), properties)
                : CREATORS.get(form).create(properties);
    }

    @FunctionalInterface
    private interface BlockCreator {
        Block create(BlockBehaviour.Properties properties);
    }

}