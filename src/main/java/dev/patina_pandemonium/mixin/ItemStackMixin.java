package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.SeededBrewData;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void patina$applyVariantName(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        Component name = callback.getReturnValue();
        SeededBrewData brew = stack.get(DynamicVariantRegistry.SEEDED_BREW_DATA.get());
        CraftingChemistry.Data chemistry = stack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        VariantGenetics.Data genetics = VariantGenetics.get(stack);
        ItemVariantData data = DynamicVariantRegistry.peekItemData(stack);
        if (brew != null) name = brew.displayName(stack);
        else if (chemistry != null && PatinaRules.INSTANCE.showChemicalNames) name = CraftingChemistry.name(stack, chemistry);
        else if (genetics != null && PatinaRules.INSTANCE.showGeneticNames) name = VariantGenetics.systematicName(stack, genetics);
        else if (data != null) name = DynamicVariantRegistry.variantItemName(stack, data);
        VariantData workstation = stack.get(DynamicVariantRegistry.CRAFTING_WORKSTATION_VARIANT.get());
        if (workstation != null) name = DynamicVariantRegistry.workstationResultName(name, workstation);
        callback.setReturnValue(name);
    }

    @Inject(method = "useOn", at = @At("HEAD"))
    private void patina$beginUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.beginVariantUse((ItemStack) (Object) this);
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void patina$endUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.endVariantUse();
    }

    @Inject(method = "use", at = @At("HEAD"))
    private void patina$beginUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.beginVariantUse((ItemStack) (Object) this);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void patina$endUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.endVariantUse();
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void patina$beginFinishUsing(Level level, LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> callback) {
        PatinaGameplayEvents.beginVariantUse((ItemStack) (Object) this);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void patina$endFinishUsing(Level level, LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> callback) {
        PatinaGameplayEvents.endVariantUse();
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void patina$beginReleaseUsing(Level level, LivingEntity entity, int remainingTime, CallbackInfo callback) {
        PatinaGameplayEvents.beginVariantUse((ItemStack) (Object) this);
    }

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void patina$endReleaseUsing(Level level, LivingEntity entity, int remainingTime, CallbackInfo callback) {
        PatinaGameplayEvents.endVariantUse();
    }

    @Inject(method = "interactLivingEntity", at = @At("HEAD"))
    private void patina$beginLivingInteraction(Player player, LivingEntity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.beginVariantUse((ItemStack) (Object) this);
    }

    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void patina$endLivingInteraction(Player player, LivingEntity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        PatinaGameplayEvents.endVariantUse();
    }

}