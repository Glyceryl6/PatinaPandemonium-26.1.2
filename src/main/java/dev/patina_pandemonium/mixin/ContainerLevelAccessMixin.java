package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerLevelAccess.class)
public interface ContainerLevelAccessMixin {

    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/inventory/ContainerLevelAccess;", at = @At("HEAD"), cancellable = true)
    private static void patina$captureCutSourceView(Level level, BlockPos pos, CallbackInfoReturnable<ContainerLevelAccess> callback) {
        ContainerLevelAccess access = PatinaDelegatingBlock.captureMenuAccess(level, pos);
        if (access != null) callback.setReturnValue(access);
    }

}