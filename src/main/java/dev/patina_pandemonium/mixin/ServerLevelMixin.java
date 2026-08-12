package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Shadow
    public abstract ServerLevel getLevel();

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void patina$redirectDelegatedBlockTick(BlockPos pos, Block type, CallbackInfo callback) {
        ServerLevel level = this.getLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PatinaDelegatingBlock carrier) || carrier.source() != type) return;
        state.tick(level, pos, level.getRandom());
        callback.cancel();
    }

    @Redirect(method = "tickFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void patina$redirectDelegatedFluidTick(FluidState fluidState, ServerLevel level, BlockPos pos, BlockState blockState) {
        if (!(blockState.getBlock() instanceof PatinaDelegatingBlock carrier)) {
            fluidState.tick(level, pos, blockState);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        VariantData data = blockEntity == null ? null : DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        PatinaGameplayEvents.beginVariantUse(data);
        try {
            fluidState.tick(level, pos, carrier.sourceState(blockState));
        } finally {
            PatinaGameplayEvents.endVariantUse();
        }
    }

}
