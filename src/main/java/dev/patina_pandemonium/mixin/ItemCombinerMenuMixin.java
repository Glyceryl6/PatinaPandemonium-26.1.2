package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {

    @Shadow @Final protected ContainerLevelAccess access;

    @Shadow
    protected abstract boolean isValidBlock(BlockState state);

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void patina$acceptVariantSource(Player player, CallbackInfoReturnable<Boolean> callback) {
        this.access.evaluate((level, pos) -> {
            BlockState sourceState = PatinaDelegatingBlock.validationSourceState(level, pos);
            return sourceState != null && this.isValidBlock(sourceState)
                    && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
        }).ifPresent(callback::setReturnValue);
    }

}