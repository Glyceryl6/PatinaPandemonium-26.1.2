package dev.patina_pandemonium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.patina_pandemonium.client.PatinaClient;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void patina$beginVariantTint(
            BlockEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockEntity blockEntity = level == null ? null : level.getBlockEntity(state.blockPos);
        VariantData data = blockEntity == null ? PatinaClient.cachedBlockVariant(state.blockPos)
            : PatinaClient.blockVariantForParticle(state.blockPos, blockEntity.getBlockState());
        PatinaClient.beginModelTint(data == null ? -1 : data.tint());
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void patina$endVariantTint(
            BlockEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera, CallbackInfo ci) {
        PatinaClient.endModelTint();
    }

}