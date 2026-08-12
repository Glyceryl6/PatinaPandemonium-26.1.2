package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.client.PatinaClient;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    @Inject(method = "tesselate", at = @At("HEAD"))
    private void patina$beginVariantTint(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo callback) {
        VariantData data = level.getModelData(pos).get(PatinaVariantBlockEntity.MODEL_DATA);
        PatinaClient.beginModelTint(data == null ? -1 : data.tint());
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void patina$endVariantTint(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo callback) {
        PatinaClient.endModelTint();
    }

    @ModifyVariable(method = "addFace", at = @At("HEAD"), argsOnly = true, name = "color")
    private int patina$tintFluid(int color) {
        return PatinaClient.applyModelTint(color);
    }

}