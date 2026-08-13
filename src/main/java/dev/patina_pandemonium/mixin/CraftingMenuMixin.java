package dev.patina_pandemonium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Redirect(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack patina$inheritCraftingVariant(CraftingRecipe recipe, RecipeInput recipeInput, @Local(name = "input") CraftingInput input) {
        return DynamicVariantRegistry.inheritCraftingVariant(input, recipe.assemble(input), "craft:" + BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
    }

}
