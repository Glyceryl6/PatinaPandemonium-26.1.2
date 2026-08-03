package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, PatinaPandemonium.MOD_ID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.patina_pandemonium"))
                .icon(() -> DynamicVariantRegistry.VARIANT_FABRICATOR_ITEM.get().getDefaultInstance())
                .displayItems((_, output) -> {
                    PatinaRules rules = PatinaRules.INSTANCE;
                    VariantForm[] forms = VariantForm.values();
                    OxidationStage[] stages = OxidationStage.values();
                    boolean[] waxStates = {false, true};
                    int rowSize = forms.length;
                    int maximumItems = Math.max(0, rules.maximumCreativeTabItems);
                    boolean limitedItems = maximumItems > 0;
                    int maximumVariantItems = limitedItems ? Math.max(0, maximumItems - 1) / rowSize * rowSize : 0;
                    int maximumSources = Math.max(0, rules.maximumCreativePreviewSources);
                    int sourceCount = 0;
                    int itemCount = 0;
                    sources:
                    for (Identifier sourceId : DynamicVariantRegistry.sourceIds()) {
                        if (maximumSources > 0 && sourceCount >= maximumSources) break;
                        for (OxidationStage stage : stages) {
                            for (boolean waxed : waxStates) {
                                if (limitedItems && itemCount + rowSize > maximumVariantItems) break sources;
                                for (VariantForm form : forms) {
                                    output.accept(DynamicVariantRegistry.displayStack(new VariantData(sourceId, stage, waxed, form, null)));
                                    itemCount++;
                                }
                            }
                        }

                        sourceCount++;
                    }

                    output.accept(DynamicVariantRegistry.VARIANT_FABRICATOR_ITEM.get());
                }).build());
    }

}