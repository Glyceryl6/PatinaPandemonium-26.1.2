package dev.patina_pandemonium.compat.jei;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JeiPlugin
public class PatinaJeiPlugin implements IModPlugin, ISimpleRecipeManagerPlugin<RecipeHolder<CraftingRecipe>> {

    private static final Identifier PLUGIN_ID = PatinaPandemonium.id("jei");
    private @Nullable IVanillaRecipeFactory recipeFactory;

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        DynamicVariantRegistry.blockVariantItems().forEach(item ->
            registration.registerFromDataComponentTypes(item, DynamicVariantRegistry.VARIANT_DATA.get()));
        DynamicVariantRegistry.standaloneVariantItems().forEach(item ->
            registration.registerFromDataComponentTypes(item, DynamicVariantRegistry.ITEM_VARIANT_DATA.get()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(DynamicVariantRegistry.VARIANT_FABRICATOR.get(),
            Component.translatable("jei.patina_pandemonium.variant_fabricator"),
            Component.translatable("jei.patina_pandemonium.crafting_inheritance"),
            Component.translatable("jei.patina_pandemonium.variant_risks"));
        registration.addIngredientInfo(Items.HONEYCOMB,
            Component.translatable("jei.patina_pandemonium.variant_waxing"));
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        this.recipeFactory = registration.getJeiHelpers().getVanillaRecipeFactory();
        registration.addSimpleRecipeManagerPlugin(RecipeTypes.CRAFTING, this);
    }

    @Override
    public boolean isHandledInput(ITypedIngredient<?> input) {
        ItemStack stack = input.getCastIngredient(VanillaTypes.ITEM_STACK);
        return stack != null && stack.getItem() != Items.HONEYCOMB && !DynamicVariantRegistry.waxedCopy(stack.copyWithCount(1)).isEmpty();
    }

    @Override
    public boolean isHandledOutput(ITypedIngredient<?> output) {
        ItemStack stack = output.getCastIngredient(VanillaTypes.ITEM_STACK);
        if (stack == null) return false;
        ItemStack unwaxed = this.unwaxedCopy(stack.copyWithCount(1));
        return !unwaxed.isEmpty() && !DynamicVariantRegistry.waxedCopy(unwaxed).isEmpty();
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> getRecipesForInput(ITypedIngredient<?> input) {
        ItemStack stack = input.getCastIngredient(VanillaTypes.ITEM_STACK);
        RecipeHolder<CraftingRecipe> recipe = stack == null ? null : this.waxingRecipe(stack.copyWithCount(1));
        return recipe == null ? List.of() : List.of(recipe);
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> getRecipesForOutput(ITypedIngredient<?> output) {
        ItemStack stack = output.getCastIngredient(VanillaTypes.ITEM_STACK);
        ItemStack unwaxed = stack == null ? ItemStack.EMPTY : this.unwaxedCopy(stack.copyWithCount(1));
        RecipeHolder<CraftingRecipe> recipe = unwaxed.isEmpty() ? null : this.waxingRecipe(unwaxed);
        return recipe == null ? List.of() : List.of(recipe);
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> getAllRecipes() {
        return List.of();
    }

    @Nullable
    private RecipeHolder<CraftingRecipe> waxingRecipe(ItemStack input) {
        IVanillaRecipeFactory factory = this.recipeFactory;
        if (factory == null) return null;
        ItemStack output = DynamicVariantRegistry.waxedCopy(input);
        if (output.isEmpty()) return null;
        SlotDisplay inputDisplay = new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(input));
        SlotDisplay outputDisplay = new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(output));
        CraftingRecipe recipe = factory.createShapedRecipeBuilder(CraftingBookCategory.MISC, outputDisplay)
            .group(PatinaPandemonium.MOD_ID + ".variant_waxing")
            .define('v', Ingredient.of(input.getItem()), inputDisplay)
            .define('h', Ingredient.of(Items.HONEYCOMB)).pattern("vh").build();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(input.getItem());
        String hash = Integer.toUnsignedString(ItemStack.hashItemAndComponents(input), 16);
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, PatinaPandemonium.id(
            "jei/variant_waxing/" + itemId.getNamespace() + "/" + itemId.getPath() + "/" + hash));
        return new RecipeHolder<>(key, recipe);
    }

    private ItemStack unwaxedCopy(ItemStack output) {
        VariantData blockData = output.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null && blockData.waxed()) {
            return DynamicVariantRegistry.transform(output, blockData.stage(), false, blockData.dyeColor());
        }
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(output);
        if (itemData != null && itemData.waxed()) {
            return DynamicVariantRegistry.transform(output, itemData.stage(), false, itemData.dyeColor());
        }
        return ItemStack.EMPTY;
    }

}