package dev.patina_pandemonium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartTntRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntMinecartRenderer.class)
public class TntMinecartRendererMixin {

    @Inject(method = "submitMinecartContents*", at = @At("HEAD"))
    private void patina$applyTntVariantTint(MinecartTntRenderState state, BlockModelRenderState blockModel, PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo callback) {
        PatinaClient.applyBlockModelTint(blockModel, PatinaClient.entityTint(state));
    }

}