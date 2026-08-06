package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.client.PatinaClient;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.world.entity.item.PrimedTnt;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntRenderer.class)
public class TntRendererMixin {

    @Inject(method = "extractRenderState*", at = @At("TAIL"))
    private void patina$applyVariantTint(PrimedTnt entity, TntRenderState state, float partialTicks, CallbackInfo callback) {
        AttachmentType<ItemVariantData> type = DynamicVariantRegistry.ENTITY_VARIANT_DATA.get();
        if (entity.hasData(type)) PatinaClient.applyBlockModelTint(state, entity.getData(type).tint());
    }

}
