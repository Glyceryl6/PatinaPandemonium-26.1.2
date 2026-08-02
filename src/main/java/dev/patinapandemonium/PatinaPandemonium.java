package dev.patinapandemonium;

import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.ModCreativeTab;
import dev.patinapandemonium.resource.RuntimePack;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(PatinaPandemonium.MOD_ID)
public class PatinaPandemonium {

    public static final String MOD_ID = "patina_pandemonium";

    public PatinaPandemonium(IEventBus modBus) {
        RuntimePack.bootstrap();
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, DynamicVariantRegistry::onRegister);
        modBus.addListener(DynamicVariantRegistry::onBlockEntityTypeAddBlocks);
        modBus.addListener(RuntimePack::onAddPackFinders);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

}