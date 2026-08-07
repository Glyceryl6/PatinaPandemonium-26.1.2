package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

}