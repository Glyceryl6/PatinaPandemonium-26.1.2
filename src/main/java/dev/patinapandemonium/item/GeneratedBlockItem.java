package dev.patinapandemonium.item;

import dev.patinapandemonium.registry.VariantData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class GeneratedBlockItem extends BlockItem {

    private final Block source;
    private final VariantData data;

    public GeneratedBlockItem(Block block, Block source, VariantData data, Item.Properties properties) {
        super(block, properties);
        this.source = source;
        this.data = data;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "block.patina_pandemonium.composed",
                Component.translatable(this.data.stageKey()),
                Component.translatable(this.data.dyeKey()),
                this.source.getName(),
                Component.translatable(this.data.formKey()));
    }

}