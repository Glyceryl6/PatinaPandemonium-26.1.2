package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void patina$useSourceStateDuringDelegation(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        BlockState state = callback.getReturnValue();
        BlockState sourceState = PatinaDelegatingBlock.sourceView(state);
        if (sourceState != state) callback.setReturnValue(sourceState);
    }

}