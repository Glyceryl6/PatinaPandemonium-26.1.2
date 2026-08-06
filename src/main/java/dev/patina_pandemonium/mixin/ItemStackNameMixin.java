package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackNameMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void patina$applyVariantName(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        ItemVariantData data = DynamicVariantRegistry.itemData(stack);
        if (data != null) callback.setReturnValue(DynamicVariantRegistry.variantItemName(stack, data));
    }

}
