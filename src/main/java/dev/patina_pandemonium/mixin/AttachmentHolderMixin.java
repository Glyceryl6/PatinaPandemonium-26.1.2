package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AttachmentHolder.class)
public class AttachmentHolderMixin {

    @Inject(method = "setData", at = @At("RETURN"))
    private <T> void patina$refreshVariantModelData(AttachmentType<T> type, T data, CallbackInfoReturnable<T> callback) {
        if (type == DynamicVariantRegistry.BLOCK_ENTITY_VARIANT_DATA.get()) {
            if ((Object) this instanceof BlockEntity blockEntity) {
                Level level = blockEntity.getLevel();
                if (level == null || !level.isClientSide()) return;
                blockEntity.requestModelDataUpdate();
                BlockState state = blockEntity.getBlockState();
                level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_IMMEDIATE);
            }
        }
    }

}