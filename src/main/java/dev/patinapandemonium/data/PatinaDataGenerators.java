package dev.patinapandemonium.data;

import dev.patinapandemonium.PatinaPandemonium;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Runtime packs provide all required resources. runData remains an optional server-data export and
 * deliberately skips the much larger client model and texture set.
 */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class PatinaDataGenerators {

    @SubscribeEvent
    public static void server(GatherDataEvent.Server event) {
        event.createProvider(PatinaRecipeProvider::new);
    }

}