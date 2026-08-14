package dev.patina_pandemonium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Inject(method = "handlePickItemFromBlock", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;tryPickItem(Lnet/minecraft/world/item/ItemStack;)V"))
    public void patina$handlePickItemFromBlock(
            ServerboundPickItemFromBlockPacket packet, CallbackInfo ci,
            @Local(name = "level") ServerLevel level,
            @Local(name = "blockState") BlockState blockState,
            @Local(name = "itemStack") LocalRef<ItemStack> itemStack) {
        if (blockState.getBlock() instanceof PatinaOxidizable oxidizable) {
            itemStack.set(oxidizable.patinaCloneItemStack(level, packet.pos(), blockState));
        }
    }

    @Redirect(method = "handlePickItemFromEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getPickResult()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack patina$handlePickItemFromEntity(Entity entity) {
        ItemStack transformed = DynamicVariantRegistry.entityStack(entity, true);
        return transformed.isEmpty() ? entity.getPickResult() : transformed;
    }

}
