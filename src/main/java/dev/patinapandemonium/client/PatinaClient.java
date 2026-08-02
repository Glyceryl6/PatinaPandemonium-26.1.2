package dev.patinapandemonium.client;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.DynamicWoodTypes;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers generated sign material families after all dynamic WoodTypes exist.
 */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class PatinaClient {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DynamicWoodTypes.values().forEach(Sheets::addWoodType));
    }

}