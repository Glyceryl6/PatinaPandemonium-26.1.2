package dev.patinapandemonium.item;

import dev.patinapandemonium.registry.VariantData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;

public final class GeneratedSignItem extends SignItem {
    private final Block source;
    private final VariantData data;

    public GeneratedSignItem(Block sign, Block wallSign, Block source, VariantData data, Item.Properties properties) {
        super(sign, wallSign, properties);
        this.source = source;
        this.data = data;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "block.patina_pandemonium.composed",
                Component.translatable(this.data.stageKey()),
                this.source.getName(),
                Component.translatable(this.data.formKey()));
    }

}