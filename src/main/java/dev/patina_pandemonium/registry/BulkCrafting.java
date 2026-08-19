package dev.patina_pandemonium.registry;

import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

/**
 * Handles one-shot bulk crafting requests. The client cancels the normal result-slot click and sends only the current
 * menu id; the server validates that menu and recipe before consuming every occupied ingredient stack. The crafted
 * stack still has the recipe's normal output count, while chemistry/provenance receives the pre-consumption counts.
 */
public class BulkCrafting {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BulkCraftPayload.TYPE, BulkCraftPayload.STREAM_CODEC,
            (payload, context) -> executeBulk(context.player(), payload.containerId()));
    }

    public static boolean executeBulk(Player player, int containerId) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (!(containerMenu instanceof AbstractCraftingMenu menu) || containerMenu.containerId != containerId || menu.getInputGridSlots().isEmpty()) return false;
        Slot resultSlot = menu.getResultSlot();
        if (resultSlot.getItem().isEmpty() || !(resultSlot.container instanceof ResultContainer resultSlots)) return false;
        if (!(menu.getInputGridSlots().getFirst().container instanceof CraftingContainer craftSlots)) return false;

        CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) return false;

        CraftingRecipe craftingRecipe = recipe.get().value();
        ItemStack base = craftingRecipe.assemble(input);
        if (base.isEmpty()) return false;
        ItemStack workstation = CraftingWorkstationContext.workstation(player);
        ItemStack batchStack = DynamicVariantRegistry.inheritCraftingVariant(input, base,
            "craft:" + BuiltInRegistries.RECIPE_SERIALIZER.getKey(craftingRecipe.getSerializer()), workstation);
        if (batchStack.isEmpty()) return false;

        // Keep the full input stacks alive until every provenance/crafting hook has observed their actual multiplicity.
        batchStack.onCraftedBy(player, batchStack.getCount());
        EventHooks.firePlayerCraftingEvent(player, batchStack, craftSlots);
        resultSlots.awardUsedRecipes(player, craftSlots.getItems());

        NonNullList<ItemStack> remaining = craftingRecipe.getRemainingItems(input);
        int gridWidth = craftSlots.getWidth();
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int slot = x + positioned.left() + (y + positioned.top()) * gridWidth;
                ItemStack ingredient = craftSlots.getItem(slot);
                ItemStack replacement = remaining.get(x + y * input.width());
                if (ingredient.isEmpty()) {
                    if (!replacement.isEmpty()) giveToPlayer(player, replacement.copy());
                    continue;
                }

                int consumed = ingredient.getCount();
                craftSlots.removeItemNoUpdate(slot);
                if (!replacement.isEmpty()) placeRemainder(craftSlots, slot, replacement, consumed, player);
            }
        }

        deliver(player, menu, batchStack);
        resultSlots.setItem(0, ItemStack.EMPTY);
        menu.slotsChanged(craftSlots);
        menu.broadcastChanges();
        return true;
    }

    /** Returns one recipe remainder for every consumed unit, filling the original grid slot first and then inventory/drop. */
    private static void placeRemainder(CraftingContainer craftSlots, int slot, ItemStack replacement, int consumed, Player player) {
        int maxStack = Math.max(1, replacement.getMaxStackSize());
        long total = (long) replacement.getCount() * consumed;
        int inGrid = (int) Math.min(maxStack, total);
        if (inGrid > 0) craftSlots.setItem(slot, replacement.copyWithCount(inGrid));
        total -= inGrid;
        while (total > 0L) {
            int count = (int) Math.min(maxStack, total);
            giveToPlayer(player, replacement.copyWithCount(count));
            total -= count;
        }
    }

    /** Keeps the single recipe output on the cursor when possible; incompatible/overflow output goes to inventory/drop. */
    private static void deliver(Player player, AbstractContainerMenu menu, ItemStack batchStack) {
        int total = batchStack.getCount();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            int count = Math.min(Math.max(1, batchStack.getMaxStackSize()), total);
            menu.setCarried(batchStack.copyWithCount(count));
            total -= count;
        } else if (ItemStack.isSameItemSameComponents(carried, batchStack)) {
            int count = Math.min(Math.max(0, carried.getMaxStackSize() - carried.getCount()), total);
            carried.grow(count);
            total -= count;
        }
        if (total > 0) giveToPlayer(player, batchStack.copyWithCount(total));
    }

    private static void giveToPlayer(Player player, ItemStack stack) {
        int maxStack = Math.max(1, stack.getMaxStackSize());
        while (!stack.isEmpty()) {
            int count = Math.min(maxStack, stack.getCount());
            ItemStack chunk = stack.copyWithCount(count);
            stack.shrink(count);
            if (!player.getInventory().add(chunk)) player.drop(chunk, false);
        }
    }

    public record BulkCraftPayload(int containerId) implements CustomPacketPayload {

        public static final Type<BulkCraftPayload> TYPE = new Type<>(PatinaPandemonium.id("bulk_craft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BulkCraftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BulkCraftPayload::containerId, BulkCraftPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

    }

}
