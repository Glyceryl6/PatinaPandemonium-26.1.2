package dev.patina_pandemonium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlameFeatureRenderer.class)
public class FlameFeatureRendererMixin {

    @Inject(method = "renderFlame", at = @At("HEAD"))
    private void patina$beginVariantTint(PoseStack.Pose pose, MultiBufferSource bufferSource, EntityRenderState state,
                                         Quaternionf rotation, AtlasManager atlasManager, CallbackInfo callback) {
        PatinaClient.beginModelTint(PatinaClient.fireTint(state));
    }

    @Inject(method = "renderFlame", at = @At("RETURN"))
    private void patina$endVariantTint(PoseStack.Pose pose, MultiBufferSource bufferSource, EntityRenderState state,
                                       Quaternionf rotation, AtlasManager atlasManager, CallbackInfo callback) {
        PatinaClient.endModelTint();
    }

    @ModifyConstant(method = "fireVertex", constant = @Constant(intValue = -1))
    private static int patina$tintFlame(int color) {
        return PatinaClient.applyModelTint(color);
    }

}