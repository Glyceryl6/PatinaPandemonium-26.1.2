package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.client.PatinaClient;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({SubmitNodeStorage.class, SubmitNodeCollection.class})
public class SubmitNodeCollectorMixin {

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, name = "tintedColor")
    private int patina$tintModel(int tintedColor) {
        return PatinaClient.applyModelTint(tintedColor);
    }

    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true, name = "tintedColor")
    private int patina$tintModelPart(int tintedColor) {
        return PatinaClient.applyModelTint(tintedColor);
    }

    @ModifyVariable(method = "submitBlockModel", at = @At("HEAD"), argsOnly = true, name = "tintLayers")
    private int[] patina$tintBlockModel(int[] tintLayers) {
        return PatinaClient.applyModelTints(tintLayers);
    }

}