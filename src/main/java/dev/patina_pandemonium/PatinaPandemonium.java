package dev.patina_pandemonium;

import dev.patina_pandemonium.network.PatinaHudSync;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ModCreativeTab;
import dev.patina_pandemonium.resource.RuntimePack;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(PatinaPandemonium.MOD_ID)
public class PatinaPandemonium {

    public static final String MOD_ID = "patina_pandemonium";

    public PatinaPandemonium(IEventBus modBus) {
        RuntimePack.register(modBus);
        modBus.addListener(PatinaHudSync::register);
        DynamicVariantRegistry.register(modBus);
        ModCreativeTab.register(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

}