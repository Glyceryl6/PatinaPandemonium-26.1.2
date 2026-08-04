package dev.patinapandemonium.item;

import dev.patinapandemonium.block.PatinaDelegatingBlock;
import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class GeneratedBlockItem extends BlockItem {

    private final VariantForm form;

    public GeneratedBlockItem(Block block, VariantForm form, Item.Properties properties) {
        super(block, properties);
        this.form = form;
    }

    public VariantForm form() {
        return this.form;
    }

    @Override
    public Component getName(ItemStack stack) {
        VariantData data = DynamicVariantRegistry.data(stack, this.form);
        return DynamicVariantRegistry.generatedBlockName(stack, data);
    }

    @Override
    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockItem sourceItem = this.sourceBlockItem();
        return sourceItem == null ? super.updatePlacementContext(context) : sourceItem.updatePlacementContext(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!(level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity)) return changed;
        VariantData data = DynamicVariantRegistry.data(stack, this.form);
        Identifier sourceId = DynamicVariantRegistry.sourceId(state.getBlock());
        blockEntity.setData(sourceId == null ? data : data.withSourceId(sourceId));
        return true;
    }

    @Nullable
    private BlockItem sourceBlockItem() {
        if (!(this.getBlock() instanceof PatinaDelegatingBlock delegated)) return null;
        Item sourceItem = delegated.source().asItem();
        return sourceItem instanceof BlockItem blockItem ? blockItem : null;
    }

}