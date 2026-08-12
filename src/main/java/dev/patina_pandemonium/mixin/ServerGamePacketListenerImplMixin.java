package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Redirect(method = "handlePickItemFromEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getPickResult()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack patina$handlePickItemFromEntity(Entity entity) {
        ItemVariantData data = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemStack picked = entity.getPickResult();
        if (picked == null || picked.isEmpty() || data == null) return picked;
        ItemStack transformed = DynamicVariantRegistry.transform(picked, data.stage(), data.waxed(), data.dyeColor());
        return transformed.isEmpty() ? picked : transformed;
    }

}