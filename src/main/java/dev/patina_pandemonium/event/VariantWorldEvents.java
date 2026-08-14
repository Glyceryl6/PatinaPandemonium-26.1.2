package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.block.PatinaDelegatingBlock;
import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

import static dev.patina_pandemonium.event.PatinaGameplayEvents.*;

/** Event handlers grouped by gameplay responsibility. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantWorldEvents {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        processFireReplacements(level);
        processLightningStrikes(level);
        processTreeGrowths(level);
        processToolTransformations(level);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        BlockEntity blockEntity = event.getBlockEntity();
        if (blockEntity == null) return;
        VariantData data = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        VariantProvenance.Data provenance = DynamicVariantRegistry.blockEntityProvenance(blockEntity);
        CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
        if (data == null) {
            if (provenance == null && chemistry == null) return;
            Item sourceItem = event.getState().getBlock().asItem();
            if (sourceItem == Items.AIR) return;
            for (ItemEntity drop : event.getDrops()) {
                if (drop.getItem().getItem() != sourceItem) continue;
                if (provenance != null) drop.getItem().set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
                if (chemistry != null) drop.getItem().set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
            }
            return;
        }

        if (!(event.getState().getBlock() instanceof PatinaOxidizable)) {
            Item sourceItem = event.getState().getBlock().asItem();
            if (sourceItem == Items.AIR) return;
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (stack.getItem() != sourceItem) continue;
                if (provenance != null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
                if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
                ItemStack transformed = DynamicVariantRegistry.transform(stack, data.stage(), data.waxed(), data.dyeColor(), data.customColor());
                if (!transformed.isEmpty()) drop.setItem(transformed);
            }
            return;
        }

        Block source = sourceBlock(event.getState().getBlock());
        if (data.form() == VariantForm.FULL) {
            BlockState sourceState = event.getState().getBlock() instanceof PatinaDelegatingBlock delegated
                ? delegated.sourceState(event.getState()) : source.defaultBlockState();
            List<ItemStack> sourceDrops = Block.getDrops(
                sourceState, event.getLevel(), event.getPos(), null, event.getBreaker(), event.getTool());
            event.getDrops().clear();
            for (ItemStack stack : sourceDrops) {
                if (provenance != null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
                if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
                ItemStack transformed = DynamicVariantRegistry.transform(stack, data.stage(), data.waxed(), data.dyeColor(), data.customColor());
                if (transformed.isEmpty()) transformed = stack;
                event.getDrops().add(new ItemEntity(event.getLevel(), event.getPos().getX() + 0.5D,
                    event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D, transformed));
            }
            return;
        }

        event.getDrops().clear();
        BlockPos pos = event.getPos();
        int count = event.getState().getBlock() instanceof SlabBlock
            && event.getState().getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        ItemStack stack = DynamicVariantRegistry.stack(data, count);
        if (provenance != null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), provenance);
        if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDropToolHistory(BlockDropsEvent event) {
        if (!PatinaRules.INSTANCE.trackToolProvenance || event.getTool().isEmpty()) return;
        String target = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            ItemStack snapshot = stack.copy();
            VariantProvenance.toolProcess(snapshot, stack, event.getTool(), "block_drop_tool", target);
        }
    }

}
