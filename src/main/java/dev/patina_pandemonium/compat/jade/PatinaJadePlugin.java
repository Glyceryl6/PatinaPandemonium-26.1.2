package dev.patina_pandemonium.compat.jade;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.BoxElement;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.ui.Rect2f;
import snownee.jade.api.ui.TooltipAnimation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Synchronizes and replaces Jade object titles only for the variant currently being inspected. */
@WailaPlugin
public class PatinaJadePlugin implements IWailaPlugin {

    private static final Identifier BLOCK_NAME = PatinaPandemonium.id("block_systematic_name");
    private static final Identifier ENTITY_NAME = PatinaPandemonium.id("entity_systematic_name");
    private static final Identifier ENTITY_GENETICS_NAME = PatinaPandemonium.id("entity_genetics_systematic_name");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BlockNameDataProvider.INSTANCE, BlockEntity.class);
        registration.registerEntityDataProvider(EntityNameDataProvider.INSTANCE, Entity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback(Integer.MAX_VALUE, ClientTitle::replaceBlockObjectName);
        registration.addBeforeRenderCallback(Integer.MAX_VALUE, ClientTitle::preserveReadableScale);
        registration.registerEntityComponent(EntityNameComponentProvider.INSTANCE, Entity.class);
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
            if (chemistry != null) stack.set(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get(), data == null ? chemistry
                : CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()));
            return stack.getHoverName();
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
            return ComponentSerialization.STREAM_CODEC;
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            return blockEntity != null && (DynamicVariantRegistry.sourceId(accessor.getBlock()) != null
                || DynamicVariantRegistry.blockEntityVariantData(blockEntity) != null
                || DynamicVariantRegistry.blockEntityChemistry(blockEntity) != null);
        }

        @Override
        public @NonNull Identifier getUid() {
            return BLOCK_NAME;
        }

    }

    private static class EntityNameDataProvider implements StreamServerDataProvider<EntityAccessor, EntityHudData> {

        private static final EntityNameDataProvider INSTANCE = new EntityNameDataProvider();
        private static final StreamCodec<RegistryFriendlyByteBuf, EntityHudData> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, EntityHudData::title,
            ComponentSerialization.STREAM_CODEC, EntityHudData::sourceName,
            ComponentSerialization.STREAM_CODEC, EntityHudData::geneticsName,
            EntityHudData::new);

        @Override
        public EntityHudData streamData(EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            ItemVariantData variant = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
            CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
            VariantGenetics.Data genetics = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
            if (variant == null && chemistry == null && genetics == null) return null;
            Component sourceName = entity.getName();
            Component title = sourceName;
            if (chemistry != null && PatinaRules.INSTANCE.showChemicalNames) {
                CraftingChemistry.Data displayChemistry = variant == null ? chemistry
                    : CraftingChemistry.retarget(chemistry, variant.stage(), variant.waxed(), variant.dyeColor(), variant.customColor());
                title = CraftingChemistry.name(displayChemistry, sourceName);
            } else if (variant != null) {
                title = DynamicVariantRegistry.variantName(variant, sourceName);
            }
            Component geneticsName = genetics != null && PatinaRules.INSTANCE.showGeneticNames
                ? VariantGenetics.systematicName(genetics, sourceName) : Component.empty();
            return new EntityHudData(title, sourceName, geneticsName);
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, EntityHudData> streamCodec() {
            return STREAM_CODEC;
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

    private record EntityHudData(Component title, Component sourceName, Component geneticsName) { }

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
            Optional<EntityHudData> data = EntityNameDataProvider.INSTANCE.decodeFromData(accessor);
            data.ifPresent(hud -> {
                ClientTitle.replaceEntityObjectName(tooltip, hud.title(), hud.sourceName());
                if (hud.geneticsName().getString().isEmpty()) return;
                MutableComponent genetics = IThemeHelper.get().info(Component.translatable("jade.patina_pandemonium.genetics_name", hud.geneticsName()));
                for (Component wrapped : ClientTitle.wrap(genetics)) tooltip.add(wrapped, ENTITY_GENETICS_NAME);
            });
        }

    }

    private static class ClientTitle {

        private static void replaceBlockObjectName(BoxElement rootElement, Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor blockAccessor)) return;
            ITooltip tooltip = rootElement.getTooltip();
            Component name = BlockNameDataProvider.INSTANCE.decodeFromData(blockAccessor).orElseGet(() -> {
                String currentName = tooltip.getString(JadeIds.CORE_OBJECT_NAME);
                if (!currentName.isEmpty()) return Component.literal(currentName);
                ItemStack picked = blockAccessor.getPickedResult();
                return picked.isEmpty() ? blockAccessor.getBlock().getName() : picked.getHoverName();
            });
            replaceObjectName(tooltip, name, null);
            rootElement.updateSize();
        }

        private static void replaceEntityObjectName(ITooltip tooltip, Component name, Component sourceName) {
            replaceObjectName(tooltip, name, sourceName.getString());
        }

        private static void replaceObjectName(ITooltip tooltip, Component name, String protectedSuffix) {
            List<Component> lines = wrap(IThemeHelper.get().title(name), protectedSuffix);
            boolean replaced = tooltip.replace(JadeIds.CORE_OBJECT_NAME, ignored -> {
                ArrayList<List<LayoutElement>> elements = new ArrayList<>(lines.size());
                for (Component line : lines) elements.add(List.of(JadeUI.text(line)));
                return elements;
            });
            if (replaced) return;
            for (int index = 0; index < lines.size(); index++) tooltip.add(index, lines.get(index), JadeIds.CORE_OBJECT_NAME);
        }

        private static List<Component> wrap(MutableComponent title) {
            return wrap(title, null);
        }

        private static List<Component> wrap(MutableComponent title, @Nullable String protectedSuffix) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            int guiWidth = minecraft.getWindow().getGuiScaledWidth();
            int availableWidth = Math.max(96, guiWidth - 48);
            int maximumWidth = Math.clamp(guiWidth * 9L / 10, 192, availableWidth);
            if (font.width(title) <= maximumWidth) return List.of(title);
            String text = title.getString();
            ArrayList<Component> lines = new ArrayList<>();
            while (!text.isEmpty()) {
                String line = font.plainSubstrByWidth(text, maximumWidth);
                if (line.isEmpty()) line = text.substring(0, text.offsetByCodePoints(0, 1));
                int preferredBreak = preferredBreak(line);
                while (preferredBreak > 0 && protectedSuffix != null
                    && text.substring(preferredBreak).stripLeading().equals(protectedSuffix)) {
                    preferredBreak = preferredBreak(line.substring(0, Math.max(0, preferredBreak - 1)));
                }
                if (preferredBreak > 0 && preferredBreak >= line.length() / 2) line = line.substring(0, preferredBreak);
                lines.add(Component.literal(line.stripTrailing()).withStyle(title.getStyle()));
                text = text.substring(line.length()).stripLeading();
            }
            keepSuffixWithPrefix(lines, title, protectedSuffix);
            return lines;
        }


        private static void keepSuffixWithPrefix(ArrayList<Component> lines, MutableComponent title, String protectedSuffix) {
            if (protectedSuffix == null || protectedSuffix.isEmpty() || lines.size() < 2) return;
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex).getString();
            if (!last.equals(protectedSuffix)) return;
            String previous = lines.remove(lastIndex - 1).getString();
            lines.removeLast();
            String separator = previous.endsWith("-") || !title.getString().endsWith("-" + protectedSuffix) ? "" : "-";
            lines.add(Component.literal(previous + separator + last).withStyle(title.getStyle()));
        }

        private static int preferredBreak(String line) {
            for (int index = line.length(); index > 0; index--) {
                int codePoint = line.codePointBefore(index);
                if (codePoint == ',' && index < line.length() && Character.isWhitespace(line.charAt(index))) return index;
                if (codePoint == '-' || codePoint == ')' || codePoint == ']' || codePoint == '}' || codePoint == ';'
                    || codePoint == '，' || codePoint == '、' || codePoint == '·' || codePoint == '/' || codePoint == '+') return index;
                index -= Character.charCount(codePoint) - 1;
            }
            return -1;
        }

        private static boolean preserveReadableScale(BoxElement root, TooltipAnimation animation, GuiGraphicsExtractor graphics, Accessor<?> accessor) {
            if (!hasPatinaHud(accessor)) return false;
            IWailaConfig config = IWailaConfig.get();
            float currentScale = animation.scale;
            float desiredScale = config.overlay().getOverlayScale();
            if (currentScale <= 0 || desiredScale <= 0 || currentScale >= desiredScale) return false;
            float contentWidth = animation.expectedRect.getWidth() / currentScale;
            float contentHeight = animation.expectedRect.getHeight() / currentScale;
            float anchorX = config.accessibility().tryFlip(config.overlay().getAnchorX());
            float anchorY = config.overlay().getAnchorY();
            resizeAroundAnchor(animation.expectedRect, contentWidth * desiredScale, contentHeight * desiredScale, anchorX, anchorY);
            resizeAroundAnchor(animation.rect, contentWidth * desiredScale, contentHeight * desiredScale, anchorX, anchorY);
            animation.scale = desiredScale;
            animation.startTime = -1;
            return false;
        }

        private static boolean hasPatinaHud(Accessor<?> accessor) {
            if (accessor instanceof BlockAccessor blockAccessor) return BlockNameDataProvider.INSTANCE.decodeFromData(blockAccessor).isPresent();
            if (accessor instanceof EntityAccessor entityAccessor) return EntityNameDataProvider.INSTANCE.decodeFromData(entityAccessor).isPresent();
            return false;
        }

        private static void resizeAroundAnchor(Rect2f rect, float width, float height, float anchorX, float anchorY) {
            float fixedX = rect.getX() + rect.getWidth() * anchorX;
            float fixedY = rect.getY() + rect.getHeight() * anchorY;
            rect.setWidth((int) width);
            rect.setHeight((int) height);
            rect.setX((int) (fixedX - rect.getWidth() * anchorX));
            rect.setY((int) (fixedY - rect.getHeight() * anchorY));
        }

    }

}
