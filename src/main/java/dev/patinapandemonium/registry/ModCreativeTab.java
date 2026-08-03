package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.patina_pandemonium"))
                .icon(Items.HONEYCOMB::getDefaultInstance)
                .displayItems((_, output) -> {
                    PatinaRules rules = PatinaRules.INSTANCE;
                    int maximumItems = Math.max(0, rules.maximumCreativeTabItems);
                    int maximumSources = Math.max(0, rules.maximumCreativePreviewSources);
                    int sourceCount = 0;
                    int itemCount = 0;
                    for (Identifier sourceId : DynamicVariantRegistry.sourceIds()) {
                        if (maximumSources > 0 && sourceCount >= maximumSources) break;
                        for (VariantForm form : VariantForm.values()) {
                            if (maximumItems > 0 && itemCount >= maximumItems) return;
                            output.accept(DynamicVariantRegistry.stack(new VariantData(
                                    sourceId, OxidationStage.FRESH, false, form, null)));
                            itemCount++;
                        }
                        sourceCount++;
                    }
                }).build());
    }

}