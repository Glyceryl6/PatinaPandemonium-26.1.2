package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantProvenance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers the concrete assemble implementation shared by cooking and stonecutting recipes. */
@Mixin(SingleItemRecipe.class)
public abstract class SingleItemRecipeMixin {

    @Inject(method = "assemble*", at = @At("RETURN"), cancellable = true)
    private void patina$recordSingleItemProcess(SingleRecipeInput input, CallbackInfoReturnable<ItemStack> callback) {
        ItemStack output = callback.getReturnValue();
        if (output.isEmpty()) return;
        ItemStack source = input.getItem(0);
        output = DynamicVariantRegistry.inheritSingleItemVariant(source, output);
        callback.setReturnValue(output);
        SingleItemRecipe recipe = (SingleItemRecipe) (Object) this;
        RecipeType<?> type = recipe.getType();
        boolean heated = type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING;
        String operation = (heated ? "heat:" : "process:") + BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        VariantProvenance.singleItemProcess(source, output, heated ? VariantProvenance.NodeType.HEAT : VariantProvenance.NodeType.PROCESS,
            operation, VariantProvenance.attributes("recipe_type", type.toString()));
    }

}