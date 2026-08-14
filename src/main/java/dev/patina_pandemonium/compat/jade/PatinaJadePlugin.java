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

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

/** Sends systematic variant names only for the object currently inspected by Jade. */
@WailaPlugin
public class PatinaJadePlugin implements IWailaPlugin {

    private static final Identifier BLOCK_NAME = PatinaPandemonium.id("block_systematic_name");
    private static final Identifier ENTITY_NAME = PatinaPandemonium.id("entity_systematic_name");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BlockNameProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(EntityNameProvider.INSTANCE, Entity.class);
    }

    private static class BlockNameProvider implements StreamServerDataProvider<BlockAccessor, Component>, IBlockComponentProvider {

        private static final BlockNameProvider INSTANCE = new BlockNameProvider();

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
        public @NonNull Identifier getUid() {
            return BLOCK_NAME;
        }

        @ParametersAreNonnullByDefault
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Optional<Component> name = this.decodeFromData(accessor);
            name.ifPresent(component -> tooltip.add(Component.translatable("jade.patina_pandemonium.systematic_name", component)));
        }

    }

    private static class EntityNameProvider implements StreamServerDataProvider<EntityAccessor, Component>, IEntityComponentProvider {

        private static final EntityNameProvider INSTANCE = new EntityNameProvider();

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
        public @NonNull Identifier getUid() {
            return ENTITY_NAME;
        }

        @ParametersAreNonnullByDefault
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            Optional<Component> name = this.decodeFromData(accessor);
            name.ifPresent(component -> tooltip.add(Component.translatable("jade.patina_pandemonium.systematic_name", component)));
        }

    }

}