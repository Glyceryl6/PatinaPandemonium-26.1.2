package dev.patinapandemonium.data;

import dev.patinapandemonium.PatinaPandemonium;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Runtime model discovery and gameplay fallbacks no longer depend on runData. The task remains an
 * optional server-data export and deliberately skips the much larger client model and texture set.
 */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class PatinaDataGenerators {

    @SubscribeEvent
    public static void server(GatherDataEvent.Server event) {
        event.createProvider(PatinaRecipeProvider::new);
    }

}