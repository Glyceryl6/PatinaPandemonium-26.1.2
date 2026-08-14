package dev.patina_pandemonium.compat.jade;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

/** Synchronizes and replaces Jade object titles only for the variant currently being inspected. */
@WailaPlugin
public class PatinaJadePlugin implements IWailaPlugin {

    private static final Identifier BLOCK_NAME = PatinaPandemonium.id("block_systematic_name");
    private static final Identifier ENTITY_NAME = PatinaPandemonium.id("entity_systematic_name");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BlockNameDataProvider.INSTANCE, BlockEntity.class);
        registration.registerEntityDataProvider(EntityNameDataProvider.INSTANCE, Entity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BlockNameComponentProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(EntityNameComponentProvider.INSTANCE, Entity.class);
    }

    private static void replaceObjectName(ITooltip tooltip, Component name) {
        Component title = IThemeHelper.get().title(name);
        if (!tooltip.replace(JadeIds.CORE_OBJECT_NAME, title)) tooltip.add(0, title, JadeIds.CORE_OBJECT_NAME);
    }

    private static class BlockNameDataProvider implements StreamServerDataProvider<BlockAccessor, Component> {

        private static final BlockNameDataProvider INSTANCE = new BlockNameDataProvider();

        @Override
        public Component streamData(BlockAccessor accessor) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            if (blockEntity == null) return null;
            VariantData data = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
            CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
            if (data == null && chemistry == null) return null;
            ItemStack stack;
            if (data != null) {
                stack = DynamicVariantRegistry.stack(data.normalized(data.form()));
            } else {
                stack = accessor.getBlock().asItem().getDefaultInstance();
            }

            if (stack.isEmpty()) return null;
            if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), chemistry);
            return stack.getHoverName();
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
            return ComponentSerialization.STREAM_CODEC;
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            return blockEntity != null && (DynamicVariantRegistry.blockEntityVariantData(blockEntity) != null
                || DynamicVariantRegistry.blockEntityChemistry(blockEntity) != null);
        }

        @Override
        public @NonNull Identifier getUid() {
            return BLOCK_NAME;
        }

    }

    private static class BlockNameComponentProvider implements IBlockComponentProvider {

        private static final BlockNameComponentProvider INSTANCE = new BlockNameComponentProvider();

        @Override
        public @NonNull Identifier getUid() {
            return BLOCK_NAME;
        }

        @Override
        public int getDefaultPriority() {
            return TooltipPosition.HEAD - 50;
        }

        @ParametersAreNonnullByDefault
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Optional<Component> name = BlockNameDataProvider.INSTANCE.decodeFromData(accessor);
            name.ifPresent(component -> replaceObjectName(tooltip, component));
        }

    }

    private static class EntityNameDataProvider implements StreamServerDataProvider<EntityAccessor, Component> {

        private static final EntityNameDataProvider INSTANCE = new EntityNameDataProvider();

        @Override
        public Component streamData(EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            ItemVariantData variant = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
            CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
            VariantGenetics.Data genetics = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
            if (variant == null && chemistry == null && genetics == null) return null;
            ItemStack stack = DynamicVariantRegistry.entityStack(entity, false);
            if (!stack.isEmpty()) return stack.getHoverName();
            return genetics == null ? entity.getName() : VariantGenetics.compactPedigree(genetics).copy().append(" ").append(entity.getName());
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
            return ComponentSerialization.STREAM_CODEC;
        }

        @Override
        public boolean shouldRequestData(EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            return entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()) != null
                || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get()) != null
                || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get()) != null;
        }

        @Override
        public @NonNull Identifier getUid() {
            return ENTITY_NAME;
        }

    }

    private static class EntityNameComponentProvider implements IEntityComponentProvider {

        private static final EntityNameComponentProvider INSTANCE = new EntityNameComponentProvider();

        @Override
        public @NonNull Identifier getUid() {
            return ENTITY_NAME;
        }

        @Override
        public int getDefaultPriority() {
            return TooltipPosition.HEAD - 50;
        }

        @ParametersAreNonnullByDefault
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            Optional<Component> name = EntityNameDataProvider.INSTANCE.decodeFromData(accessor);
            name.ifPresent(component -> replaceObjectName(tooltip, component));
        }

    }

}