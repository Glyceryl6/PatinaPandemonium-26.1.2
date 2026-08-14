package dev.patina_pandemonium.compat.jade;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.network.PatinaHudSync;
import dev.patina_pandemonium.registry.CraftingChemistry;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantGenetics;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.WailaPlugin;
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

/** Uses Jade only as a target/request marker while large names travel through Patina's clientbound HUD channel. */
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

    private static class BlockNameDataProvider implements StreamServerDataProvider<BlockAccessor, Long> {

        private static final BlockNameDataProvider INSTANCE = new BlockNameDataProvider();
        private static final StreamCodec<RegistryFriendlyByteBuf, Long> STREAM_CODEC = ByteBufCodecs.VAR_LONG.mapStream(buffer -> (ByteBuf) buffer);

        @Override
        public Long streamData(BlockAccessor accessor) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            if (blockEntity == null) return null;
            VariantData data = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
            CraftingChemistry.Data chemistry = DynamicVariantRegistry.blockEntityChemistry(blockEntity);
            if (data == null && chemistry == null) return null;
            long revision = revision(data, chemistry, null, accessor.getBlock().getName(), PatinaRules.INSTANCE.showChemicalNames, false);
            if (accessor.getPlayer() instanceof ServerPlayer player) {
                long targetId = blockEntity.getBlockPos().asLong();
                PatinaHudSync.sendBlock(player, targetId, revision, () -> blockHud(accessor, data, chemistry));
            }
            return revision;
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, Long> streamCodec() {
            return STREAM_CODEC;
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

        private static PatinaHudSync.HudText blockHud(BlockAccessor accessor, VariantData data, CraftingChemistry.Data chemistry) {
            Component sourceName;
            Component title;
            if (data != null) {
                ItemStack stack = DynamicVariantRegistry.stack(data.normalized(data.form()));
                sourceName = stack.isEmpty() ? accessor.getBlock().getName() : CraftingChemistry.sourceName(stack);
                if (chemistry != null && PatinaRules.INSTANCE.showChemicalNames) {
                    title = CraftingChemistry.name(CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor()), sourceName);
                } else {
                    title = stack.isEmpty() ? DynamicVariantRegistry.variantName(data, sourceName) : stack.getHoverName();
                }
            } else {
                sourceName = accessor.getBlock().getName();
                title = chemistry != null && PatinaRules.INSTANCE.showChemicalNames ? CraftingChemistry.name(chemistry, sourceName) : sourceName;
            }
            return new PatinaHudSync.HudText(title, sourceName, Component.empty());
        }

    }

    private static class EntityNameDataProvider implements StreamServerDataProvider<EntityAccessor, Long> {

        private static final EntityNameDataProvider INSTANCE = new EntityNameDataProvider();
        private static final StreamCodec<RegistryFriendlyByteBuf, Long> STREAM_CODEC = ByteBufCodecs.VAR_LONG.mapStream(buffer -> (ByteBuf) buffer);

        @Override
        public Long streamData(EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            ItemVariantData variant = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
            CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
            VariantGenetics.Data genetics = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
            if (variant == null && chemistry == null && genetics == null) return null;
            Component sourceName = entity.getName();
            long revision = revision(variant, chemistry, genetics, sourceName, PatinaRules.INSTANCE.showChemicalNames, PatinaRules.INSTANCE.showGeneticNames);
            if (accessor.getPlayer() instanceof ServerPlayer player) {
                PatinaHudSync.sendEntity(player, entity.getId(), revision, () -> entityHud(variant, chemistry, genetics, sourceName));
            }
            return revision;
        }

        @Override
        public @NonNull StreamCodec<RegistryFriendlyByteBuf, Long> streamCodec() {
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

        private static PatinaHudSync.HudText entityHud(ItemVariantData variant, CraftingChemistry.Data chemistry,
                                                        VariantGenetics.Data genetics, Component sourceName) {
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
            return new PatinaHudSync.HudText(title, sourceName, geneticsName);
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
            Optional<Long> revision = EntityNameDataProvider.INSTANCE.decodeFromData(accessor);
            if (revision.isEmpty()) return;
            PatinaHudSync.HudText hud = PatinaHudSync.entity(accessor.getEntity().getId(), revision.get());
            if (hud == null) return;
            ClientTitle.replaceEntityObjectName(tooltip, hud.title(), hud.sourceName());
            if (hud.geneticsName().getString().isEmpty()) return;
            MutableComponent geneticsName = hud.geneticsName().copy().withStyle(ChatFormatting.DARK_AQUA);
            MutableComponent genetics = Component.translatable("jade.patina_pandemonium.genetics_name", geneticsName)
                .withStyle(ChatFormatting.DARK_AQUA);
            for (Component wrapped : ClientTitle.wrap(genetics, null)) tooltip.add(wrapped, ENTITY_GENETICS_NAME);
        }
    }

    private static class ClientTitle {

        private static void replaceBlockObjectName(BoxElement rootElement, Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor blockAccessor)) return;
            Optional<Long> revision = BlockNameDataProvider.INSTANCE.decodeFromData(blockAccessor);
            if (revision.isEmpty() || blockAccessor.getBlockEntity() == null) return;
            PatinaHudSync.HudText hud = PatinaHudSync.block(blockAccessor.getBlockEntity().getBlockPos().asLong(), revision.get());
            if (hud == null) return;
            replaceObjectName(rootElement.getTooltip(), hud.title(), hud.sourceName().getString());
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

        private static List<Component> wrap(MutableComponent title, String protectedSuffix) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            int guiWidth = minecraft.getWindow().getGuiScaledWidth();
            int maximumWidth = Math.max(96, guiWidth - 32);
            if (font.width(title) <= maximumWidth) return List.of(title);
            String text = title.getString();
            if (protectedSuffix != null && !protectedSuffix.isEmpty() && text.endsWith(protectedSuffix)
                && font.width(protectedSuffix) < maximumWidth) return wrapWithAttachedSuffix(text, protectedSuffix, title, maximumWidth, font);
            return wrapText(text, title, maximumWidth, font);
        }

        private static List<Component> wrapWithAttachedSuffix(String text, String suffix, MutableComponent title, int maximumWidth, Font font) {
            String prefix = text.substring(0, text.length() - suffix.length());
            int suffixBudget = maximumWidth - font.width(suffix);
            if (prefix.isEmpty() || suffixBudget <= 0) return wrapText(text, title, maximumWidth, font);
            String reversedPrefix = new StringBuilder(prefix).reverse().toString();
            String reversedTail = font.plainSubstrByWidth(reversedPrefix, suffixBudget);
            int tailLength = Math.max(1, Math.min(prefix.length(), reversedTail.length()));
            int tailStart = prefix.length() - tailLength;
            int preferred = preferredBreakForward(prefix, tailStart);
            if (preferred > tailStart && preferred < prefix.length() && font.width(prefix.substring(preferred) + suffix) <= maximumWidth) tailStart = preferred;
            tailStart = ensureVisiblePrefix(prefix, tailStart);
            String head = prefix.substring(0, tailStart);
            String tail = prefix.substring(tailStart) + suffix;
            ArrayList<Component> lines = new ArrayList<>(wrapText(head, title, maximumWidth, font));
            if (lines.size() == 1 && lines.getFirst().getString().isEmpty()) lines.clear();
            lines.add(Component.literal(tail.stripLeading()).withStyle(title.getStyle()));
            return lines;
        }

        private static List<Component> wrapText(String value, MutableComponent title, int maximumWidth, Font font) {
            ArrayList<Component> lines = new ArrayList<>();
            String text = value;
            while (!text.isEmpty()) {
                String line = font.plainSubstrByWidth(text, maximumWidth);
                if (line.isEmpty()) line = text.substring(0, text.offsetByCodePoints(0, 1));
                int preferredBreak = preferredBreak(line);
                if (preferredBreak > 0 && preferredBreak >= line.length() / 2) line = line.substring(0, preferredBreak);
                lines.add(Component.literal(line.stripTrailing()).withStyle(title.getStyle()));
                text = text.substring(line.length()).stripLeading();
            }
            if (lines.isEmpty()) lines.add(Component.empty().withStyle(title.getStyle()));
            return lines;
        }

        private static int ensureVisiblePrefix(String prefix, int start) {
            int index = Math.clamp(start, 0, prefix.length());
            while (index > 0 && !containsVisibleNamePart(prefix, index)) {
                int codePoint = prefix.codePointBefore(index);
                index -= Character.charCount(codePoint);
            }
            return index;
        }

        private static boolean containsVisibleNamePart(String text, int start) {
            for (int index = start; index < text.length();) {
                int codePoint = text.codePointAt(index);
                if (!Character.isWhitespace(codePoint) && !isBreak(codePoint)) return true;
                index += Character.charCount(codePoint);
            }
            return false;
        }

        private static int preferredBreakForward(String text, int start) {
            for (int index = Math.max(0, start); index < text.length(); index++) {
                int codePoint = text.codePointAt(index);
                if (isBreak(codePoint)) return index + Character.charCount(codePoint);
                index += Character.charCount(codePoint) - 1;
            }
            return -1;
        }

        private static int preferredBreak(String line) {
            for (int index = line.length(); index > 0; index--) {
                int codePoint = line.codePointBefore(index);
                if (codePoint == ',' && index < line.length() && Character.isWhitespace(line.charAt(index))) return index;
                if (isBreak(codePoint)) return index;
                index -= Character.charCount(codePoint) - 1;
            }
            return -1;
        }

        private static boolean isBreak(int codePoint) {
            return codePoint == '-' || codePoint == ')' || codePoint == ']' || codePoint == '}' || codePoint == ';'
                || codePoint == '，' || codePoint == '、' || codePoint == '·' || codePoint == '/' || codePoint == '+';
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

    private static long revision(Object variant, CraftingChemistry.Data chemistry, VariantGenetics.Data genetics,
                                 Component sourceName, boolean showChemistry, boolean showGenetics) {
        long value = variant == null ? 0L : variant.hashCode();
        value = value * 31L + (chemistry == null ? 0L : chemistry.signature());
        value = value * 31L + (genetics == null ? 0L : genetics.lineageSignature());
        value = value * 31L + sourceName.hashCode();
        value = value * 31L + (showChemistry ? 1L : 0L);
        return value * 31L + (showGenetics ? 1L : 0L);
    }

}