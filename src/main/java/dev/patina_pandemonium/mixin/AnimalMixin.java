package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.VariantGenetics;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public class AnimalMixin {

    @Inject(method = "setInLove", at = @At("TAIL"))
    private void patina$adjustLoveDuration(Player player, CallbackInfo ci) {
        VariantGenetics.adjustLoveDuration((Animal) (Object) this);
    }

}