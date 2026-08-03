package dev.patinapandemonium.item;

import dev.patinapandemonium.block.PatinaOxidizable;
import dev.patinapandemonium.registry.VariantData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class GeneratedBlockItem extends BlockItem {

    public GeneratedBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        VariantData data = ((PatinaOxidizable) this.getBlock()).patinaData();
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        if (source == Blocks.AIR) source = this.getBlock();
        return Component.translatable(
            "block.patina_pandemonium.composed",
            Component.translatable(data.stageKey()),
            Component.translatable(data.dyeKey()),
            source.getName(),
            Component.translatable(data.formKey()));
    }

}