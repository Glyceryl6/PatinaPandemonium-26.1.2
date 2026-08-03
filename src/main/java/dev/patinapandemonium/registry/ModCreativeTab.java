package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.patina_pandemonium"))
                .icon(Items.HONEYCOMB::getDefaultInstance)
                .displayItems((_, output) -> {
                    int maximum = Math.max(0, PatinaRules.INSTANCE.maximumCreativeTabItems);
                    int added = 0;
                    for (Block block : DynamicVariantRegistry.generated()) {
                        if (maximum > 0 && added >= maximum) break;
                        Item item = block.asItem();
                        if (item == Items.AIR) continue;
                        output.accept(item);
                        added++;
                    }
                }).build());
    }

}