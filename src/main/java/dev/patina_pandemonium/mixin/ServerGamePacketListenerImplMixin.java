package dev.patina_pandemonium.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import dev.patina_pandemonium.registry.VariantProvenance;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.List;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Inject(method = "handlePickItemFromBlock", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;tryPickItem(Lnet/minecraft/world/item/ItemStack;)V"))
    public void patina$handlePickItemFromBlock(
            ServerboundPickItemFromBlockPacket packet, CallbackInfo ci,
            @Local(name = "level") ServerLevel level,
            @Local(name = "blockState") BlockState blockState,
            @Local(name = "itemStack") ItemStack itemStack) {
        if (blockState.getBlock() instanceof PatinaOxidizable) {
            ServerGamePacketListenerImpl.addBlockDataToItem(blockState, level, packet.pos(), itemStack);
        }
    }

    @Redirect(method = "handlePickItemFromEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getPickResult()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack patina$handlePickItemFromEntity(Entity entity) {
        ItemVariantData data = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemStack picked = entity.getPickResult();
        if (picked == null || picked.isEmpty() || data == null) return picked;
        ItemStack transformed = DynamicVariantRegistry.transform(picked, data.stage(), data.waxed(), data.dyeColor(), data.customColor());
        if (transformed.isEmpty()) return picked;
        CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
        if (chemistry != null) {
            transformed.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(),
                CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
        } else if (transformed.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get()) == null) {
            CraftingChemistry.Synthesis synthesis = CraftingChemistry.synthesize(CraftingInput.of(1, 1, List.of(transformed)));
            if (synthesis != null) transformed.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), synthesis.data());
        }

        VariantGenetics.Data genetics = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
        if (genetics == null) genetics = VariantGenetics.initialize(entity);
        transformed.set(DynamicVariantRegistry.GENETICS.get(), genetics);
        VariantProvenance.Data provenance = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get());
        if (provenance == null) {
            provenance = VariantProvenance.entitySource(entity);
            entity.setData(DynamicVariantRegistry.ENTITY_PROVENANCE.get(), provenance);
        }

        transformed.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
        return transformed;
    }

}