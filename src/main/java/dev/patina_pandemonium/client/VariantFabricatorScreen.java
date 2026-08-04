package dev.patina_pandemonium.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.patina_pandemonium.menu.VariantFabricatorMenu;
import dev.patina_pandemonium.registry.OxidationStage;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class VariantFabricatorScreen extends AbstractContainerScreen<VariantFabricatorMenu> {

    private static final int BACKGROUND = 0xFF151B1E;
    private static final int PANEL = 0xFF222D31;
    private static final int PANEL_INNER = 0xFF101719;
    private static final int BORDER = 0xFF5B7774;
    private static final int SELECTED = 0xFF6FD4BE;
    private static final int HOVERED = 0xFFA8E6D9;
    private static final int TEXT = 0xFFE8F2EF;
    private static final int MUTED_TEXT = 0xFF9FB5AF;
    private static final int FORM_X = 10;
    private static final int FORM_Y = 30;
    private static final int FORM_SIZE = 18;
    private static final int DYE_X = 10;
    private static final int DYE_Y = 100;
    private static final int DYE_SIZE = 16;
    private static final int STAGE_X = 100;
    private static final int STAGE_Y = 30;
    private static final int STAGE_SIZE = 18;
    private static final int RESULT_X = 180;
    private static final int RESULT_Y = 30;
    private static final int RESULT_SIZE = 22;

    public VariantFabricatorScreen(VariantFabricatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 340, 230);
        this.inventoryLabelX = 82;
        this.inventoryLabelY = 128;
        menu.registerUpdateListener(() -> {});
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, BACKGROUND);
        graphics.outline(left, top, this.imageWidth, this.imageHeight, BORDER);
        this.panel(graphics, left + 6, top + 18, 82, 134);
        this.panel(graphics, left + 94, top + 18, 80, 88);
        this.panel(graphics, left + 176, top + 18, 96, 76);
        this.panel(graphics, left + 278, top + 18, 56, 88);
        this.panel(graphics, left + 78, top + 140, 184, 84);
        graphics.text(this.font, Component.translatable("container.patina_pandemonium.forms"), left + 10, top + 20, MUTED_TEXT, false);
        graphics.text(this.font, Component.translatable("container.patina_pandemonium.colors"), left + 10, top + 88, MUTED_TEXT, false);
        graphics.text(this.font, Component.translatable("container.patina_pandemonium.oxidation"), left + 100, top + 20, MUTED_TEXT, false);
        graphics.text(this.font, Component.translatable("container.patina_pandemonium.variants"), left + 180, top + 20, MUTED_TEXT, false);
        graphics.centeredText(this.font, Component.translatable("container.patina_pandemonium.output"), left + 306, top + 20, MUTED_TEXT);
        this.extractFormButtons(graphics, mouseX, mouseY);
        this.extractDyeButtons(graphics, mouseX, mouseY);
        this.extractStageButtons(graphics, mouseX, mouseY);
        this.extractResultButtons(graphics, mouseX, mouseY);
        this.extractOutputPreview(graphics);
        this.slotFrame(graphics, left + 68, top + 50);
        this.slotFrame(graphics, left + 300, top + 66);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.slotFrame(graphics, left + 82 + column * 18, top + 146 + row * 18);
            }
        }

        for (int column = 0; column < 9; column++) {
            this.slotFrame(graphics, left + 82 + column * 18, top + 204);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, 8, 6, TEXT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MUTED_TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int relativeX = mouseX - this.leftPos;
        int relativeY = mouseY - this.topPos;
        int form = gridIndex(relativeX, relativeY, FORM_X, FORM_Y, FORM_SIZE, 3, 3);
        if (form >= 0 && form < VariantForm.values().length) {
            VariantForm variantForm = VariantForm.byOrdinal(form);
            Component name = Component.translatable("patina_pandemonium.form." + variantForm.id());
            graphics.setTooltipForNextFrame(this.font, this.menu.supportsForm(variantForm)
                ? name
                : Component.translatable("container.patina_pandemonium.form_unavailable", name), mouseX, mouseY);
            return;
        }
        int dye = gridIndex(relativeX, relativeY, DYE_X, DYE_Y, DYE_SIZE, 4, 4);
        if (dye >= 0 && dye < DyeColor.VALUES.size()) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable(
                "patina_pandemonium.dye." + DyeColor.VALUES.get(dye).getSerializedName()), mouseX, mouseY);
            return;
        }
        int stage = gridIndex(relativeX, relativeY, STAGE_X, STAGE_Y, STAGE_SIZE, 4, 1);
        if (stage >= 0 && stage < OxidationStage.values().length) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable(
                "patina_pandemonium.stage." + OxidationStage.byOrdinal(stage).id()), mouseX, mouseY);
            return;
        }
        if (inside(relativeX, relativeY, STAGE_X, STAGE_Y + 24, 68, 14)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("container.patina_pandemonium.clear_stages.tooltip"), mouseX, mouseY);
            return;
        }
        int result = gridIndex(relativeX, relativeY, RESULT_X, RESULT_Y, RESULT_SIZE, 4, 2);
        List<VariantData> variants = this.menu.visibleVariants();
        if (result >= 0 && result < variants.size()) {
            VariantData data = variants.get(result);
            ItemStack stack = this.menu.preview(data.form(), data.stage(), data.waxed(), data.dyeColor());
            graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - this.leftPos;
        double y = event.y() - this.topPos;
        int buttonId = -1;
        int form = gridIndex(x, y, FORM_X, FORM_Y, FORM_SIZE, 3, 3);
        if (form >= 0 && form < VariantForm.values().length && this.menu.supportsForm(VariantForm.byOrdinal(form))) {
            buttonId = VariantFabricatorMenu.FORM_BUTTON_START + form;
        }
        int dye = gridIndex(x, y, DYE_X, DYE_Y, DYE_SIZE, 4, 4);
        if (buttonId < 0 && dye >= 0 && dye < DyeColor.VALUES.size()) buttonId = VariantFabricatorMenu.DYE_BUTTON_START + dye;
        int stage = gridIndex(x, y, STAGE_X, STAGE_Y, STAGE_SIZE, 4, 1);
        if (buttonId < 0 && stage >= 0 && stage < OxidationStage.values().length) buttonId = VariantFabricatorMenu.STAGE_BUTTON_START + stage;
        if (buttonId < 0 && inside(x, y, STAGE_X, STAGE_Y + 24, 72, 14)) buttonId = VariantFabricatorMenu.CLEAR_STAGES_BUTTON;
        int result = gridIndex(x, y, RESULT_X, RESULT_Y, RESULT_SIZE, 4, 2);
        if (buttonId < 0 && result >= 0 && result < this.menu.visibleVariants().size()) buttonId = VariantFabricatorMenu.RESULT_BUTTON_START + result;
        if (this.minecraft.player != null && buttonId >= 0 && this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            if (this.minecraft.gameMode != null) this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void extractFormButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int index = 0; index < VariantForm.values().length; index++) {
            int x = this.leftPos + FORM_X + index % 3 * FORM_SIZE;
            int y = this.topPos + FORM_Y + index / 3 * FORM_SIZE;
            VariantForm form = VariantForm.byOrdinal(index);
            boolean enabled = this.menu.supportsForm(form);
            boolean selected = this.menu.selectedForm() == form;
            this.button(graphics, x, y, FORM_SIZE, FORM_SIZE, selected, inside(mouseX, mouseY, x, y, FORM_SIZE, FORM_SIZE), enabled);
            ItemStack stack = this.menu.preview(form, OxidationStage.FRESH, false, this.menu.selectedDye());
            if (!stack.isEmpty()) graphics.item(stack, x + 1, y + 1);
        }
    }

    private void extractDyeButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        DyeColor selected = this.menu.selectedDye();
        for (int index = 0; index < DyeColor.VALUES.size(); index++) {
            DyeColor dye = DyeColor.VALUES.get(index);
            int x = this.leftPos + DYE_X + index % 4 * DYE_SIZE;
            int y = this.topPos + DYE_Y + index / 4 * DYE_SIZE;
            this.button(graphics, x, y, DYE_SIZE, DYE_SIZE, selected == dye, inside(mouseX, mouseY, x, y, DYE_SIZE, DYE_SIZE));
            graphics.fill(x + 3, y + 3, x + DYE_SIZE - 3, y + DYE_SIZE - 3, 0xFF000000 | dye.getTextureDiffuseColor());
        }
    }

    private void extractStageButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int mask = this.menu.oxidationMask();
        for (OxidationStage stage : OxidationStage.values()) {
            int x = this.leftPos + STAGE_X + stage.ordinal() * STAGE_SIZE;
            int y = this.topPos + STAGE_Y;
            boolean selected = (mask & 1 << stage.ordinal()) != 0;
            this.button(graphics, x, y, STAGE_SIZE, STAGE_SIZE, selected, inside(mouseX, mouseY, x, y, STAGE_SIZE, STAGE_SIZE));
            ItemStack stack = this.menu.preview(this.menu.selectedForm(), stage, false, this.menu.selectedDye());
            graphics.item(stack, x + 1, y + 1);
        }
        int x = this.leftPos + STAGE_X;
        int y = this.topPos + STAGE_Y + 24;
        boolean hovered = inside(mouseX, mouseY, x, y, 68, 14);
        this.button(graphics, x, y, 68, 14, mask == 0, hovered);
        graphics.centeredText(this.font, Component.translatable("container.patina_pandemonium.clear_stages"), x + 34, y + 3, TEXT);
    }

    private void extractResultButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<VariantData> variants = this.menu.visibleVariants();
        for (int index = 0; index < 8; index++) {
            int x = this.leftPos + RESULT_X + index % 4 * RESULT_SIZE;
            int y = this.topPos + RESULT_Y + index / 4 * RESULT_SIZE;
            boolean selected = this.menu.selectedResult() == index;
            this.button(graphics, x, y, RESULT_SIZE, RESULT_SIZE, selected, inside(mouseX, mouseY, x, y, RESULT_SIZE, RESULT_SIZE));
            if (index < variants.size()) {
                VariantData data = variants.get(index);
                graphics.item(this.menu.preview(data.form(), data.stage(), data.waxed(), data.dyeColor()), x + 3, y + 3);
                if (data.waxed()) graphics.fill(x + 16, y + 3, x + 20, y + 7, 0xFFE5B85C);
            }
        }
    }

    private void extractOutputPreview(GuiGraphicsExtractor graphics) {
        ItemStack stack = this.menu.getSlot(VariantFabricatorMenu.RESULT_SLOT).getItem();
        if (stack.isEmpty()) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.leftPos + 290, this.topPos + 30);
        graphics.pose().scale(2.0F, 2.0F);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL_INNER);
    }

    private void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0A0F10);
        graphics.outline(x - 1, y - 1, 18, 18, BORDER);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean selected, boolean hovered) {
        this.button(graphics, x, y, width, height, selected, hovered, true);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean selected, boolean hovered, boolean enabled) {
        graphics.fill(x, y, x + width, y + height, !enabled ? 0xFF151B1E : selected ? 0xFF254D46 : hovered ? 0xFF334744 : 0xFF1D292C);
        graphics.outline(x, y, width, height, !enabled ? 0xFF354541 : selected ? SELECTED : hovered ? HOVERED : BORDER);
        if (hovered && enabled) graphics.requestCursor(CursorTypes.POINTING_HAND);
    }

    private static int gridIndex(double x, double y, int originX, int originY, int size, int columns, int rows) {
        if (!inside(x, y, originX, originY, size * columns, size * rows)) return -1;
        return (int) ((y - originY) / size) * columns + (int) ((x - originX) / size);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

}
