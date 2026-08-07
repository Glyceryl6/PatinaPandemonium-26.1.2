package dev.patina_pandemonium.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantOffer.class)
public interface MerchantOfferAccessor {

    @Mutable
    @Accessor("result")
    void patina$setResult(ItemStack result);

    @Accessor("uses")
    void patina$setUses(int uses);

    @Accessor("demand")
    void patina$setDemand(int demand);

}