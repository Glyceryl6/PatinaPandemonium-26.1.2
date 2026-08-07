package dev.patina_pandemonium.mixin;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BlockModelRenderState.class)
public interface BlockModelRenderStateAccessor {

    @Accessor("modelParts")
    @Nullable
    List<BlockStateModelPart> patina$getModelParts();

}