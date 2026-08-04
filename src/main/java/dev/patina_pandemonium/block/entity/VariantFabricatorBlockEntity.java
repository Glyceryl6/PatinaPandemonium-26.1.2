package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.menu.VariantFabricatorMenu;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class VariantFabricatorBlockEntity extends BaseContainerBlockEntity {

    private static final Component DEFAULT_NAME = Component.translatable("container.patina_pandemonium.variant_fabricator");
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public VariantFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(DynamicVariantRegistry.VARIANT_FABRICATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && DynamicVariantRegistry.supportsFabrication(stack);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new VariantFabricatorMenu(containerId, inventory, this,
            this.level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(this.level, this.worldPosition));
    }

}