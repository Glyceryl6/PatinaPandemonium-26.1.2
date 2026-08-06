package dev.patina_pandemonium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractMinecartRenderer.class)
public class AbstractMinecartRendererMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "submit*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void patina$submitTintedModel(SubmitNodeCollector collector, Model model, Object state, PoseStack poseStack, Identifier texture, int light, int overlay, int outline, @Nullable CrumblingOverlay crumblingOverlay) {
        int tint = PatinaClient.entityTint((EntityRenderState) state);
        collector.submitModel(model, state, poseStack, model.renderType(texture), light, overlay, tint, null, outline, crumblingOverlay);
    }

}