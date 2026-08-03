package dev.patinapandemonium.item;

import dev.patinapandemonium.registry.VariantData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;

public class GeneratedSignItem extends SignItem {
    private final Block source;
    private final VariantData data;

    public GeneratedSignItem(Block sign, Block wallSign, Block source, VariantData data, Item.Properties properties) {
        super(sign, wallSign, properties);
        this.source = source;
        this.data = data;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.empty()
            .append(Component.translatable(this.data.stageKey()))
            .append(Component.translatable(this.data.dyeKey()))
            .append(this.source.getName())
            .append(Component.translatable(this.data.formKey()));
    }

}
