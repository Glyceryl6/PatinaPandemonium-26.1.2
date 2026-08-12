package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Ingredient.class)
public abstract class IngredientMixin {

    @Shadow
    public abstract boolean test(ItemStack input);

    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void patina$acceptVariantSource(ItemStack input, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || input == null || input.isEmpty()) return;
        ItemStack source = DynamicVariantRegistry.stonecutterRecipeInput(input);
        if (source == input) return;
        callback.setReturnValue(this.test(source));
    }

}