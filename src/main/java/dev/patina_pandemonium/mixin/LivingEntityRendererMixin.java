package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void patina$applyEntityTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> callback) {
        int tint = PatinaClient.entityTint(state);
        if (tint != -1) callback.setReturnValue(ARGB.multiply(callback.getReturnValue(), tint));
    }

}