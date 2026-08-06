package dev.patina_pandemonium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CrafterBlock.class)
public class CrafterBlockMixin {

    @Redirect(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack patina$inheritCraftingVariant(CraftingRecipe recipe, RecipeInput recipeInput, @Local(name = "var11") CraftingInput var11) {
        return DynamicVariantRegistry.inheritCraftingVariant(var11, recipe.assemble(var11));
    }

}