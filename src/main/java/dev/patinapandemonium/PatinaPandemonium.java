package dev.patinapandemonium;

import dev.patinapandemonium.command.PatinaCommands;
import dev.patinapandemonium.event.PatinaGameplayEvents;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.ModCreativeTab;
import dev.patinapandemonium.registry.VariantTagInheritance;
import dev.patinapandemonium.resource.RuntimePack;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PatinaPandemonium.MOD_ID)
public class PatinaPandemonium {

    public static final String MOD_ID = "patina_pandemonium";

    public PatinaPandemonium(IEventBus modBus) {
        RuntimePack.bootstrap();
        DynamicVariantRegistry.register(modBus);
        ModCreativeTab.register(modBus);
        modBus.addListener(RuntimePack::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(PatinaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(PatinaGameplayEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(PatinaGameplayEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(PatinaGameplayEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(PatinaGameplayEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, VariantTagInheritance::onTagsUpdated);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

}