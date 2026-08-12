package dev.patina_pandemonium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @SuppressWarnings("rawtypes")
    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private <S extends EntityRenderState> void patina$submitVariantProjectile(EntityRenderer instance, S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, @Local(name = "renderer") EntityRenderer<?, ? super S> renderer) {
        int tint = PatinaClient.projectileTint(state);
        if (tint == -1) {
            renderer.submit(state, poseStack, submitNodeCollector, camera);
            return;
        }

        PatinaClient.beginModelTint(tint);
        try {
            renderer.submit(state, poseStack, submitNodeCollector, camera);
        } finally {
            PatinaClient.endModelTint();
        }
    }

}