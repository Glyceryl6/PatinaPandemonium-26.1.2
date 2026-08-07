package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.event.PatinaGameplayEvents;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTable.class)
public class LootTableMixin {

    @Inject(method = "fill", at = @At("RETURN"))
    private void patina$transformContainerLoot(Container container, LootParams params, long optionalRandomSeed, CallbackInfo callback) {
        PatinaGameplayEvents.transformGeneratedContainerLoot(container, params, optionalRandomSeed);
    }

}