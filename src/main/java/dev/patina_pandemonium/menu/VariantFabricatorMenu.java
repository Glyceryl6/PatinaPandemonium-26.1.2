package dev.patina_pandemonium.menu;

import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.OxidationStage;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VariantFabricatorMenu extends AbstractContainerMenu {

    public static final int FORM_BUTTON_START = 0;
    public static final int DYE_BUTTON_START = 16;
    public static final int STAGE_BUTTON_START = 40;
    public static final int CLEAR_STAGES_BUTTON = 44;
    public static final int RESULT_BUTTON_START = 64;
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = 29;
    private static final int HOTBAR_START = 29;
    private static final int HOTBAR_END = 38;

    private final Container input;
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final DataSlot selectedForm = DataSlot.standalone();
    private final DataSlot selectedDye = DataSlot.standalone();
    private final DataSlot oxidationMask = DataSlot.standalone();
    private final DataSlot selectedResult = DataSlot.standalone();
    private final List<VariantData> visibleVariants = new ArrayList<>(8);
    private final Slot inputSlot;
    private final Slot resultSlot;
    private ItemStack previousInput = ItemStack.EMPTY;
    private Runnable updateListener = () -> {};

    public VariantFabricatorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    public VariantFabricatorMenu(int containerId, Inventory inventory, Container input, ContainerLevelAccess access) {
        super(DynamicVariantRegistry.VARIANT_FABRICATOR_MENU.get(), containerId);
        checkContainerSize(input, 1);
        this.input = input;
        this.access = access;
        this.selectedForm.set(VariantForm.FULL.ordinal());
        this.selectedDye.set(-1);
        this.selectedResult.set(-1);
        this.inputSlot = this.addSlot(new Slot(input, 0, 68, 50) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return DynamicVariantRegistry.supportsFabrication(stack);
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.result, 0, 300, 66) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return VariantFabricatorMenu.this.isCurrentResult(this.getItem());
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (!VariantFabricatorMenu.this.isCurrentResult(stack)) {
                    VariantFabricatorMenu.this.clearResult();
                    return;
                }
                stack.onCraftedBy(player, stack.getCount());
                VariantFabricatorMenu.this.inputSlot.remove(1);
                VariantFabricatorMenu.this.refreshVariants(false);
                super.onTake(player, stack);
            }
        });
        this.addStandardInventorySlots(inventory, 82, 146);
        this.addDataSlot(this.selectedForm);
        this.addDataSlot(this.selectedDye);
        this.addDataSlot(this.oxidationMask);
        this.addDataSlot(this.selectedResult);
        this.refreshVariants(false);
    }

    public VariantForm selectedForm() {
        return VariantForm.byOrdinal(this.selectedForm.get());
    }

    @Nullable
    public DyeColor selectedDye() {
        return VariantData.dyeById(this.selectedDye.get());
    }

    public int oxidationMask() {
        return this.oxidationMask.get();
    }

    public int selectedResult() {
        return this.selectedResult.get();
    }

    public List<VariantData> visibleVariants() {
        return List.copyOf(this.visibleVariants);
    }

    public boolean supportsForm(VariantForm form) {
        return DynamicVariantRegistry.supportsForm(this.inputSlot.getItem(), form);
    }

    public ItemStack preview(VariantForm form, OxidationStage stage, boolean waxed, @Nullable DyeColor dye) {
        return DynamicVariantRegistry.fabricate(this.inputSlot.getItem(), form, stage, waxed, dye, 1);
    }

    public void registerUpdateListener(Runnable listener) {
        this.updateListener = listener;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, DynamicVariantRegistry.VARIANT_FABRICATOR.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= FORM_BUTTON_START && buttonId < FORM_BUTTON_START + VariantForm.values().length) {
            VariantForm form = VariantForm.byOrdinal(buttonId - FORM_BUTTON_START);
            if (!this.supportsForm(form)) return false;
            this.selectedForm.set(form.ordinal());
            this.refreshVariants(true);
            return true;
        }
        if (buttonId >= DYE_BUTTON_START && buttonId < DYE_BUTTON_START + DyeColor.VALUES.size()) {
            int dye = buttonId - DYE_BUTTON_START;
            this.selectedDye.set(this.selectedDye.get() == dye ? -1 : dye);
            this.refreshVariants(true);
            return true;
        }
        if (buttonId >= STAGE_BUTTON_START && buttonId < STAGE_BUTTON_START + OxidationStage.values().length) {
            this.oxidationMask.set(this.oxidationMask.get() ^ 1 << buttonId - STAGE_BUTTON_START);
            this.refreshVariants(true);
            return true;
        }
        if (buttonId == CLEAR_STAGES_BUTTON) {
            this.oxidationMask.set(0);
            this.refreshVariants(true);
            return true;
        }
        if (buttonId >= RESULT_BUTTON_START && buttonId < RESULT_BUTTON_START + this.visibleVariants.size()) {
            this.selectedResult.set(buttonId - RESULT_BUTTON_START);
            this.setupResultSlot();
            return true;
        }
        return false;
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack current = this.inputSlot.getItem();
        if (!ItemStack.isSameItemSameComponents(current, this.previousInput)) {
            this.previousInput = current.copy();
            if (!this.supportsForm(this.selectedForm())) this.selectedForm.set(VariantForm.FULL.ordinal());
            this.refreshVariants(true);
        } else if (!this.isCurrentResult(this.resultSlot.getItem())) {
            this.setupResultSlot();
        }
        this.updateListener.run();
        super.slotsChanged(container);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack clicked = stack.copy();
        if (slotIndex == RESULT_SLOT) return this.takeAllResults(player);
        if (slotIndex == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) return ItemStack.EMPTY;
        } else if (DynamicVariantRegistry.supportsFabrication(stack)) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (slotIndex >= INVENTORY_START && slotIndex < INVENTORY_END) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) return ItemStack.EMPTY;
        } else if (slotIndex >= HOTBAR_START && slotIndex < HOTBAR_END
            && !this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        slot.setChanged();
        return stack.getCount() == clicked.getCount() ? ItemStack.EMPTY : clicked;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.result && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.result.removeItemNoUpdate(0);
        this.access.execute((_, _) -> this.clearContainer(player, this.input));
    }

    private ItemStack takeAllResults(Player player) {
        ItemStack prototype = this.expectedResult();
        if (prototype.isEmpty() || !this.isCurrentResult(this.resultSlot.getItem())) {
            this.clearResult();
            return ItemStack.EMPTY;
        }

        int available = this.inputSlot.getItem().getCount();
        int moved = 0;
        while (available > 0) {
            int batchSize = Math.min(available, prototype.getMaxStackSize());
            ItemStack batch = prototype.copyWithCount(batchSize);
            if (!this.moveItemStackTo(batch, INVENTORY_START, HOTBAR_END, true)) break;
            int inserted = batchSize - batch.getCount();
            if (inserted <= 0) break;
            moved += inserted;
            available -= inserted;
        }

        if (moved <= 0) return ItemStack.EMPTY;
        this.inputSlot.remove(moved);
        prototype.onCraftedBy(player, moved);
        this.refreshVariants(false);
        this.broadcastChanges();
        return prototype.copyWithCount(moved);
    }

    private void refreshVariants(boolean resetSelection) {
        this.visibleVariants.clear();
        if (DynamicVariantRegistry.supportsFabrication(this.inputSlot.getItem())) {
            int mask = this.oxidationMask.get();
            for (OxidationStage stage : OxidationStage.values()) {
                if (mask != 0 && (mask & 1 << stage.ordinal()) == 0) continue;
                VariantData base = VariantData.defaultFor(this.selectedForm()).withStage(stage).withDye(this.selectedDye());
                this.visibleVariants.add(base.withWaxed(false));
                this.visibleVariants.add(base.withWaxed(true));
            }
        }

        if (resetSelection || this.selectedResult.get() >= this.visibleVariants.size()) this.selectedResult.set(-1);
        this.setupResultSlot();
        this.updateListener.run();
    }

    private ItemStack expectedResult() {
        int index = this.selectedResult.get();
        if (index < 0 || index >= this.visibleVariants.size()) return ItemStack.EMPTY;
        VariantData choice = this.visibleVariants.get(index);
        return this.preview(choice.form(), choice.stage(), choice.waxed(), choice.dyeColor());
    }

    private boolean isCurrentResult(ItemStack stack) {
        ItemStack expected = this.expectedResult();
        return !expected.isEmpty() && !stack.isEmpty() && ItemStack.isSameItemSameComponents(expected, stack);
    }

    private void setupResultSlot() {
        this.resultSlot.set(this.expectedResult());
        this.broadcastChanges();
    }

    private void clearResult() {
        this.resultSlot.set(ItemStack.EMPTY);
        this.broadcastChanges();
    }

}