package dev.patina_pandemonium.registry;

import com.mojang.logging.LogUtils;
import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Rebinds loaded block and item tags so every source-bound carrier is a real member of the source holder's tags. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantTagInheritance {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData() || DynamicVariantRegistry.sourceBindings().isEmpty()) return;
        int blockMemberships = inheritBlockTags();
        int itemMemberships = inheritItemTags();
        LOGGER.debug(
            "Inherited {} block-tag and {} item-tag memberships for Patina source-bound carriers after {}",
            blockMemberships, itemMemberships, event.getClass().getSimpleName());
    }

    private static int inheritBlockTags() {
        Map<TagKey<Block>, LinkedHashSet<Holder<Block>>> additions = new LinkedHashMap<>();
        for (DynamicVariantRegistry.CarrierBinding binding : DynamicVariantRegistry.sourceBindings()) {
            Block source = BuiltInRegistries.BLOCK.getValue(binding.sourceId());
            if (source == Blocks.AIR) continue;
            collect(BuiltInRegistries.BLOCK, source, binding.block(), binding.form() == VariantForm.FULL, additions);
        }
        return apply(BuiltInRegistries.BLOCK, additions);
    }

    private static int inheritItemTags() {
        Map<TagKey<Item>, LinkedHashSet<Holder<Item>>> additions = new LinkedHashMap<>();
        for (DynamicVariantRegistry.CarrierBinding binding : DynamicVariantRegistry.sourceBindings()) {
            if (binding.form() != VariantForm.FULL) continue;
            Block sourceBlock = BuiltInRegistries.BLOCK.getValue(binding.sourceId());
            Item source = sourceBlock.asItem();
            Item target = binding.item();
            if (source == Items.AIR || target == null) continue;
            collect(BuiltInRegistries.ITEM, source, target, true, additions);
        }
        return apply(BuiltInRegistries.ITEM, additions);
    }

    private static <T> void collect(Registry<T> registry, T source, T target, boolean fullForm, Map<TagKey<T>, LinkedHashSet<Holder<T>>> additions) {
        Holder<T> targetHolder = registry.wrapAsHolder(target);
        registry.wrapAsHolder(source).tags().forEach(tag -> {
            if (!fullForm && !isToolTag(tag.location().getPath())) return;
            additions.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(targetHolder);
        });
    }

    /** Form carriers keep only tool/harvest behaviour; recipe-driving tags stay on full sources so stairs never act as logs. */
    private static boolean isToolTag(String path) {
        return path.startsWith("mineable/") || path.startsWith("needs_");
    }

    private static <T> int apply(Registry<T> registry, Map<TagKey<T>, LinkedHashSet<Holder<T>>> additions) {
        int memberships = 0;
        for (Map.Entry<TagKey<T>, LinkedHashSet<Holder<T>>> entry : additions.entrySet()) {
            HolderSet.Named<T> tag = registry.get(entry.getKey()).orElse(null);
            if (tag == null) continue;
            LinkedHashSet<Holder<T>> merged = new LinkedHashSet<>(tag.stream().toList());
            int previousSize = merged.size();
            merged.addAll(entry.getValue());
            if (merged.size() == previousSize) continue;
            tag.bind(new ArrayList<>(merged));
            memberships += merged.size() - previousSize;
        }
        if (memberships > 0 && registry instanceof MappedRegistry<?> mappedRegistry) {
            mappedRegistry.refreshTagsInHolders();
        }
        return memberships;
    }

}