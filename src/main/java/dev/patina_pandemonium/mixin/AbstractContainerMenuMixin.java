package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

    @Inject(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z", at = @At("HEAD"), cancellable = true)
    private static void patina$acceptVariantSource(ContainerLevelAccess access, Player player, Block block, CallbackInfoReturnable<Boolean> callback) {
        access.evaluate((level, pos) -> PatinaDelegatingBlock.validatesAsSource(level, pos, block) && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D).ifPresent(callback::setReturnValue);
    }

}