package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;

public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.patina_pandemonium"))
                .icon(Items.HONEYCOMB::getDefaultInstance)
                .displayItems((parameters, output) -> {
                    LinkedHashSet<Item> seen = new LinkedHashSet<>();
                    for (VariantEntry entry : DynamicVariantRegistry.entries()) {
                        if (entry.data().form() == VariantForm.WALL_SIGN) {
                            continue;
                        }
                        Item item = entry.block().asItem();
                        if (item != Items.AIR && seen.add(item)) {
                            output.accept(item);
                        }
                    }
                }).build());
    }

}