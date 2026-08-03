package dev.patinapandemonium.item;

import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = Blocks.STONE;
        return Component.translatable(
                "block.patina_pandemonium.composed",
                Component.translatable(data.stageKey()),
                Component.translatable(data.dyeKey()),
                source.getName(),
                Component.translatable(data.formKey()));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        VariantData data = DynamicVariantRegistry.data(context.getItemInHand(), this.form);
        BlockPos pos = context.getClickedPos();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && context.getLevel().getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) {
            blockEntity.setData(data);
        }
        return result;
    }

}