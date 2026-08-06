package dev.patina_pandemonium.compat.jei;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class PatinaJeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_ID = PatinaPandemonium.id("jei");

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
            Component.translatable("jei.patina_pandemonium.crafting_inheritance"));
    }

}