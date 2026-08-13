package dev.patina_pandemonium.registry;

import net.neoforged.bus.api.IEventBus;

/** Registers the static registries and the runtime source-registration listeners. */
public class VariantRegistryBootstrap {

    public static void register(IEventBus modBus) {
        modBus.addListener(VariantSourceRegistration::onRegister);
        modBus.addListener(VariantSourceRegistration::onBlockEntityTypeAddBlocks);
        DynamicVariantRegistry.BLOCKS.register(modBus);
        DynamicVariantRegistry.ITEMS.register(modBus);
        DynamicVariantRegistry.COMPONENTS.register(modBus);
        DynamicVariantRegistry.RECIPE_SERIALIZERS.register(modBus);
        DynamicVariantRegistry.MOB_EFFECTS.register(modBus);
        DynamicVariantRegistry.ATTACHMENTS.register(modBus);
        DynamicVariantRegistry.BLOCK_ENTITY_TYPES.register(modBus);
        DynamicVariantRegistry.MENUS.register(modBus);
    }

}