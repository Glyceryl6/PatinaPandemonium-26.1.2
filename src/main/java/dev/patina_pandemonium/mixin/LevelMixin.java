package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void patina$preserveDelegatedSourceWrite(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> callback) {
        Level level = (Level) (Object) this;
        BlockState preserved = PatinaDelegatingBlock.preserveSourceWrite(level, pos, blockState);
        if (preserved != blockState) {
            callback.setReturnValue(level.setBlock(pos, preserved, updateFlags, updateLimit));
            return;
        }

        Block source = blockState.getBlock();
        VariantData data = PatinaGameplayEvents.outputVariant(source);
        Block carrier = data == null ? null : DynamicVariantRegistry.fullCarrier(source);
        if (carrier == null) return;
        BlockState target = carrier instanceof PatinaDelegatingBlock ? carrier.withPropertiesOf(blockState) : carrier.defaultBlockState();
        boolean changed = level.setBlock(pos, target, updateFlags, updateLimit);
        if (changed && level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(data);
        callback.setReturnValue(changed);
    }

}
