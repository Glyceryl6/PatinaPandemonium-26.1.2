package dev.patina_pandemonium.recipe;

import com.mojang.serialization.MapCodec;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A component-preserving special recipe for waxing any Patina-managed stack. */
public class VariantWaxingRecipe extends CustomRecipe {

    public static final VariantWaxingRecipe INSTANCE = new VariantWaxingRecipe();
    public static final MapCodec<VariantWaxingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, VariantWaxingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !this.findResult(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.findResult(input);
    }

    @Override
    public RecipeSerializer<VariantWaxingRecipe> getSerializer() {
        return DynamicVariantRegistry.VARIANT_WAXING_RECIPE.get();
    }

    private ItemStack findResult(CraftingInput input) {
        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (first.isEmpty()) first = stack;
            else if (second.isEmpty()) second = stack;
            else return ItemStack.EMPTY;
        }
        if (first.isEmpty() || second.isEmpty()) return ItemStack.EMPTY;
        ItemStack target = this.isPlainHoneycomb(first) ? second : this.isPlainHoneycomb(second) ? first : ItemStack.EMPTY;
        if (target.isEmpty()) return ItemStack.EMPTY;
        ItemStack output = DynamicVariantRegistry.waxedCopy(target);
        if (!output.isEmpty()) output.setCount(1);
        return output;
    }

    private boolean isPlainHoneycomb(ItemStack stack) {
        return stack.is(Items.HONEYCOMB)
            && !stack.has(DynamicVariantRegistry.VARIANT_DATA.get())
            && !stack.has(DynamicVariantRegistry.ITEM_VARIANT_DATA.get());
    }

}