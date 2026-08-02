package dev.patinapandemonium.data;

import dev.patinapandemonium.PatinaPandemonium;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class PatinaDataGenerators {

    @SubscribeEvent
    public static void client(GatherDataEvent.Client event) {
        event.createProvider(PatinaModelProvider::new);
    }

    @SubscribeEvent
    public static void server(GatherDataEvent.Server event) {
        event.createProvider(PatinaRecipeProvider::new);
        event.createProvider(PatinaDataMapProvider::new);
        event.createProvider(PatinaTagProvider::new);
    }

}