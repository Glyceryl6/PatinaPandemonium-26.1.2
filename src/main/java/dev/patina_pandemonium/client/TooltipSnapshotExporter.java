package dev.patina_pandemonium.client;

import com.mojang.datafixers.util.Either;
import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Captures the complete textual tooltip before screen wrapping and exports it independently of the inventory framebuffer. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID, value = Dist.CLIENT)
public class TooltipSnapshotExporter {

    private static final long HOVER_TIMEOUT_MILLIS = 750L;
    private static final int DOCUMENT_WIDTH = 1_440;
    private static final int DOCUMENT_PADDING = 24;
    private static final int MAX_PAGE_HEIGHT = 4_096;
    private static final int FONT_SIZE = 20;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");
    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static List<SnapshotLine> hoveredLines = List.of();
    private static Screen hoveredScreen;
    private static long hoveredAt;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureTooltip(RenderTooltipEvent.GatherComponents event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || event.getItemStack().isEmpty()) return;
        List<SnapshotLine> lines = new ArrayList<>();
        for (Either<FormattedText, TooltipComponent> element : event.getTooltipElements()) {
            element.left().ifPresent(text -> lines.add(snapshotLine(text)));
        }

        hoveredStack = event.getItemStack().copy();
        hoveredLines = List.copyOf(lines);
        hoveredScreen = minecraft.screen;
        hoveredAt = System.currentTimeMillis();
    }

    public static ItemStack hoveredStack() {
        return hasCurrentHover() ? hoveredStack.copy() : ItemStack.EMPTY;
    }

    public static boolean hasCurrentHover() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen != null && minecraft.screen == hoveredScreen && !hoveredStack.isEmpty()
            && System.currentTimeMillis() - hoveredAt <= HOVER_TIMEOUT_MILLIS;
    }

    public static @Nullable ExportResult exportCurrentTooltip() throws IOException {
        if (!hasCurrentHover() || hoveredLines.isEmpty()) return null;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
        BufferedImage metricImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricGraphics = metricImage.createGraphics();
        metricGraphics.setFont(font);
        FontMetrics metrics = metricGraphics.getFontMetrics();
        List<SnapshotLine> wrapped = new ArrayList<>();
        int contentWidth = DOCUMENT_WIDTH - DOCUMENT_PADDING * 2;
        for (SnapshotLine line : hoveredLines) wrap(line, metrics, contentWidth, wrapped);
        metricGraphics.dispose();
        int lineHeight = metrics.getHeight() + 2;
        int linesPerPage = Math.max(1, (MAX_PAGE_HEIGHT - DOCUMENT_PADDING * 2) / lineHeight);
        int pages = Math.max(1, (wrapped.size() + linesPerPage - 1) / linesPerPage);
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("patina_tooltips");
        Files.createDirectories(directory);
        String baseName = "tooltip_" + FILE_TIME.format(LocalDateTime.now());
        List<Path> outputs = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            int from = page * linesPerPage;
            int to = Math.min(wrapped.size(), from + linesPerPage);
            int height = Math.max(64, DOCUMENT_PADDING * 2 + (to - from) * lineHeight);
            BufferedImage image = new BufferedImage(DOCUMENT_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(new Color(16, 0, 16, 244));
            graphics.fillRect(0, 0, DOCUMENT_WIDTH, height);
            graphics.setColor(new Color(80, 0, 96, 255));
            graphics.drawRect(1, 1, DOCUMENT_WIDTH - 3, height - 3);
            graphics.setColor(new Color(40, 0, 48, 255));
            graphics.drawRect(2, 2, DOCUMENT_WIDTH - 5, height - 5);
            graphics.setFont(font);
            int y = DOCUMENT_PADDING + metrics.getAscent();
            for (int index = from; index < to; index++) {
                SnapshotLine line = wrapped.get(index);
                graphics.setColor(new Color(line.color(), true));
                graphics.drawString(line.text(), DOCUMENT_PADDING, y);
                y += lineHeight;
            }

            graphics.dispose();
            Path output = directory.resolve(pages == 1 ? baseName + ".png" : baseName + "_part_" + String.format("%03d", page + 1) + ".png");
            ImageIO.write(image, "PNG", output.toFile());
            outputs.add(output);
        }

        return new ExportResult(outputs, hoveredLines.stream().mapToInt(line -> line.text().codePointCount(0, line.text().length())).sum());
    }

    private static SnapshotLine snapshotLine(FormattedText text) {
        int color = 0xFFFDFDFD;
        if (text instanceof Component component && component.getStyle().getColor() != null) {
            color = 0xFF000000 | component.getStyle().getColor().getValue();
        }

        return new SnapshotLine(text.getString(), color);
    }

    private static void wrap(SnapshotLine source, FontMetrics metrics, int maximumWidth, List<SnapshotLine> output) {
        String text = source.text();
        if (text.isEmpty()) {
            output.add(source);
            return;
        }
        StringBuilder line = new StringBuilder();
        int width = 0;
        SnapshotLine snapshotLine = new SnapshotLine(line.toString(), source.color());
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int next = offset + Character.charCount(codePoint);
            if (codePoint == '\n') {
                output.add(snapshotLine);
                line.setLength(0);
                width = 0;
                offset = next;
                continue;
            }
            int codePointWidth = metrics.charWidth(codePoint);
            if (width + codePointWidth > maximumWidth && !line.isEmpty()) {
                output.add(snapshotLine);
                line.setLength(0);
                width = 0;
            }
            line.appendCodePoint(codePoint);
            width += codePointWidth;
            offset = next;
        }

        if (!line.isEmpty()) output.add(snapshotLine);
    }

    public record ExportResult(List<Path> files, int characters) {}

    private record SnapshotLine(String text, int color) {}

}