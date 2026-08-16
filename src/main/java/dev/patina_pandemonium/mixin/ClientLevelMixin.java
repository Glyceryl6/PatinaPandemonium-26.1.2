package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.client.PatinaClient;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures per-instance variant tint before vanilla creates block breaking/destroy particles. */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"))
    private void patina$captureDestroyVariant(BlockPos pos, BlockState blockState, CallbackInfo callback) {
        this.patina$rememberVariant(pos);
    }

    @Inject(method = "addBreakingBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"))
    private void patina$captureBreakingVariant(BlockPos pos, Direction direction, @Nullable HitResult hitResult, CallbackInfo callback) {
        this.patina$rememberVariant(pos);
    }

    @Unique
    private void patina$rememberVariant(BlockPos pos) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        VariantData data = blockEntity == null ? null : DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        if (data != null) PatinaClient.rememberBlockVariant(pos, data);
    }

}