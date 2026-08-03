package dev.patinapandemonium.block;

import dev.patinapandemonium.registry.VariantForm;
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

    private static final Set<VariantForm> NO_OCCLUSION_FORMS = Set.of(
            VariantForm.CARPET, VariantForm.BUTTON, VariantForm.PRESSURE_PLATE);
    private static final Set<VariantForm> NO_COLLISION_FORMS = Set.of(
            VariantForm.BUTTON, VariantForm.PRESSURE_PLATE);
    private static final Map<VariantForm, BlockCreator> CREATORS = Map.of(
            VariantForm.FULL, PatinaBlock::new,
            VariantForm.SLAB, PatinaSlabBlock::new,
            VariantForm.STAIRS, properties -> new PatinaStairBlock(Blocks.STONE.defaultBlockState(), properties),
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
        if (NO_COLLISION_FORMS.contains(form)) properties.noCollision();
        if (nonOccluding || NO_OCCLUSION_FORMS.contains(form)) properties.noOcclusion();
        return CREATORS.get(form).create(properties);
    }

    @FunctionalInterface
    private interface BlockCreator {
        Block create(BlockBehaviour.Properties properties);
    }

}