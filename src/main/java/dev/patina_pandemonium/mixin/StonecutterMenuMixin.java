package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterMenu.class)
public class StonecutterMenuMixin {

    @Shadow @Final public Container container;
    @Shadow @Final Slot resultSlot;
    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Shadow private ItemStack input;
    @Shadow private void setupResultSlot(int index) {}

    @Redirect(method = "slotsChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean patina$isSameRecipeSource(ItemStack current, Object o) {
        ItemStack currentSource = DynamicVariantRegistry.stonecutterRecipeInput(current);
        ItemStack previousSource = DynamicVariantRegistry.stonecutterRecipeInput(this.input);
        return currentSource.is(previousSource.getItem());
    }

    @ModifyVariable(method = "setupRecipeList", at = @At("HEAD"), argsOnly = true, name = "item")
    private ItemStack patina$useSourceForRecipeLookup(ItemStack item) {
        return DynamicVariantRegistry.stonecutterRecipeInput(item);
    }

    @ModifyArg(
        method = "quickMoveStack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;acceptsInput(Lnet/minecraft/world/item/ItemStack;)Z"),
        index = 0)
    private ItemStack patina$useSourceForQuickMove(ItemStack item) {
        return DynamicVariantRegistry.stonecutterRecipeInput(item);
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void patina$refreshSelectedVariantResult(Container container, CallbackInfo callback) {
        int index = this.selectedRecipeIndex.get();
        if (index >= 0) this.setupResultSlot(index);
    }

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void patina$inheritVariantResult(int index, CallbackInfo callback) {
        ItemStack output = this.resultSlot.getItem();
        if (output.isEmpty()) return;
        ItemStack transformed = DynamicVariantRegistry.inheritStonecutterVariant(this.container.getItem(0), output);
        if (transformed == output) return;
        this.resultSlot.set(transformed);
        ((StonecutterMenu) (Object) this).broadcastChanges();
    }

}