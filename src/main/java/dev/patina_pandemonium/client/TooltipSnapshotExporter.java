package dev.patina_pandemonium.client;

import com.mojang.datafixers.util.Either;
import dev.patina_pandemonium.PatinaPandemonium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
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
    private static final int DOCUMENT_PADDING = 24;
    private static final int COLUMN_GAP = 24;
    private static final int FONT_SIZE = 20;
    private static final int MIN_COLUMN_CHARACTERS = 24;
    private static final long MAX_IMAGE_PIXELS = 16_777_216L;
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
        Minecraft minecraft = Minecraft.getInstance();
        Font font = displayFont();
        BufferedImage metricImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricGraphics = metricImage.createGraphics();
        metricGraphics.setFont(font);
        FontMetrics metrics = metricGraphics.getFontMetrics();
        int lineHeight = metrics.getHeight() + 2;
        int windowWidth = Math.max(1, minecraft.getWindow().getWidth());
        int windowHeight = Math.max(1, minecraft.getWindow().getHeight());
        double aspectRatio = (double) windowWidth / windowHeight;
        SnapshotLayout layout = bestLayout(metrics, lineHeight, aspectRatio);
        metricGraphics.dispose();

        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("patina_tooltips");
        Files.createDirectories(directory);
        String baseName = "tooltip_" + FILE_TIME.format(LocalDateTime.now());
        List<Path> outputs;
        if ((long) layout.width() * layout.height() <= MAX_IMAGE_PIXELS) {
            List<SnapshotLine> wrapped = wrapAll(metrics, layout.columnWidth());
            Path output = directory.resolve(baseName + ".png");
            writePage(output, wrapped, 0, wrapped.size(), layout, font, metrics, lineHeight, true);
            outputs = List.of(output);
        } else {
            outputs = writePaged(directory, baseName, layout.columns(), font, metrics, lineHeight, aspectRatio);
        }

        int characters = hoveredLines.stream().mapToInt(line -> line.text().codePointCount(0, line.text().length())).sum();
        return new ExportResult(outputs, characters);
    }

    private static SnapshotLayout bestLayout(FontMetrics metrics, int lineHeight, double aspectRatio) {
        SnapshotLayout best = null;
        int maxColumns = Math.max(1, Math.min(8, (int) Math.ceil(aspectRatio * 3.0D)));
        int minimumColumnWidth = Math.max(1, metrics.charWidth('M')) * MIN_COLUMN_CHARACTERS;
        for (int columns = 1; columns <= maxColumns; columns++) {
            SnapshotLayout candidate = minimumLayout(metrics, lineHeight, aspectRatio, columns, minimumColumnWidth);
            if (best == null || (long) candidate.width() * candidate.height() < (long) best.width() * best.height()) best = candidate;
        }
        return best;
    }

    private static SnapshotLayout minimumLayout(FontMetrics metrics, int lineHeight, double aspectRatio, int columns, int minimumColumnWidth) {
        int low = DOCUMENT_PADDING * 2 + lineHeight;
        int high = low;
        while (!fitsHeight(metrics, lineHeight, aspectRatio, columns, minimumColumnWidth, high)) {
            if (high > Integer.MAX_VALUE / 2) throw new IllegalStateException("Tooltip snapshot dimensions overflowed");
            high *= 2;
        }

        while (low < high) {
            int middle = low + (high - low) / 2;
            if (fitsHeight(metrics, lineHeight, aspectRatio, columns, minimumColumnWidth, middle)) high = middle;
            else low = middle + 1;
        }
        int width = widthForHeight(low, aspectRatio);
        return new SnapshotLayout(width, low, columns, columnWidth(width, columns));
    }

    private static boolean fitsHeight(FontMetrics metrics, int lineHeight, double aspectRatio, int columns, int minimumColumnWidth, int height) {
        int width = widthForHeight(height, aspectRatio);
        int columnWidth = columnWidth(width, columns);
        if (columnWidth <= 0 || columns > 1 && columnWidth < minimumColumnWidth) return false;
        long lines = wrappedLineCount(metrics, columnWidth);
        long rows = (lines + columns - 1L) / columns;
        return DOCUMENT_PADDING * 2L + rows * lineHeight <= height;
    }

    private static int widthForHeight(int height, double aspectRatio) {
        long width = Math.max(DOCUMENT_PADDING * 2L + 1L, Math.round(height * aspectRatio));
        if (width > Integer.MAX_VALUE) throw new IllegalStateException("Tooltip snapshot dimensions overflowed");
        return (int) width;
    }

    private static int columnWidth(int width, int columns) {
        return (width - DOCUMENT_PADDING * 2 - COLUMN_GAP * (columns - 1)) / columns;
    }

    private static long wrappedLineCount(FontMetrics metrics, int maximumWidth) {
        long count = 0L;
        for (SnapshotLine line : hoveredLines) count += wrappedLineCount(line.text(), metrics, maximumWidth);
        return count;
    }

    private static int wrappedLineCount(String text, FontMetrics metrics, int maximumWidth) {
        if (text.isEmpty()) return 1;
        int lines = 0;
        int width = 0;
        boolean hasText = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines++;
                width = 0;
                hasText = false;
                continue;
            }
            int codePointWidth = metrics.charWidth(codePoint);
            if (width + codePointWidth > maximumWidth && hasText) {
                lines++;
                width = 0;
                hasText = false;
            }
            width += codePointWidth;
            hasText = true;
        }
        return hasText ? lines + 1 : lines;
    }

    private static List<Path> writePaged(Path directory, String baseName, int preferredColumns, Font font, FontMetrics metrics, int lineHeight, double aspectRatio) throws IOException {
        int pageHeight = Math.max(DOCUMENT_PADDING * 2 + lineHeight, (int) Math.floor(Math.sqrt(MAX_IMAGE_PIXELS / aspectRatio)));
        int pageWidth = widthForHeight(pageHeight, aspectRatio);
        while ((long) pageWidth * pageHeight > MAX_IMAGE_PIXELS && pageHeight > DOCUMENT_PADDING * 2 + lineHeight) {
            pageHeight--;
            pageWidth = widthForHeight(pageHeight, aspectRatio);
        }
        int columns = preferredColumns;
        int minimumColumnWidth = Math.max(1, metrics.charWidth('M')) * MIN_COLUMN_CHARACTERS;
        while (columns > 1 && columnWidth(pageWidth, columns) < minimumColumnWidth) columns--;
        int contentWidth = columnWidth(pageWidth, columns);
        List<SnapshotLine> wrapped = wrapAll(metrics, contentWidth);
        int rowsPerColumn = Math.max(1, (pageHeight - DOCUMENT_PADDING * 2) / lineHeight);
        int linesPerPage = Math.max(1, rowsPerColumn * columns);
        int pages = Math.max(1, (wrapped.size() + linesPerPage - 1) / linesPerPage);
        SnapshotLayout pageLayout = new SnapshotLayout(pageWidth, pageHeight, columns, contentWidth);
        List<Path> outputs = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            int from = page * linesPerPage;
            int to = Math.min(wrapped.size(), from + linesPerPage);
            Path output = directory.resolve(baseName + "_part_" + String.format("%03d", page + 1) + ".png");
            writePage(output, wrapped, from, to, pageLayout, font, metrics, lineHeight, page == pages - 1);
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    private static void writePage(Path output, List<SnapshotLine> lines, int from, int to, SnapshotLayout layout, Font font, FontMetrics metrics, int lineHeight, boolean balanceColumns) throws IOException {
        BufferedImage image = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setColor(new Color(16, 0, 16, 244));
        graphics.fillRect(0, 0, layout.width(), layout.height());
        graphics.setColor(new Color(80, 0, 96, 255));
        graphics.drawRect(1, 1, layout.width() - 3, layout.height() - 3);
        graphics.setColor(new Color(40, 0, 48, 255));
        graphics.drawRect(2, 2, layout.width() - 5, layout.height() - 5);
        graphics.setFont(font);
        int pageLines = to - from;
        int rowsPerColumn = Math.max(1, (layout.height() - DOCUMENT_PADDING * 2) / lineHeight);
        if (balanceColumns) rowsPerColumn = Math.min(rowsPerColumn, Math.max(1, (pageLines + layout.columns() - 1) / layout.columns()));
        for (int index = from; index < to; index++) {
            int localIndex = index - from;
            int column = localIndex / rowsPerColumn;
            int row = localIndex % rowsPerColumn;
            SnapshotLine line = lines.get(index);
            graphics.setColor(new Color(line.color(), true));
            graphics.drawString(line.text(), DOCUMENT_PADDING + column * (layout.columnWidth() + COLUMN_GAP), DOCUMENT_PADDING + metrics.getAscent() + row * lineHeight);
        }
        graphics.dispose();
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static List<SnapshotLine> wrapAll(FontMetrics metrics, int maximumWidth) {
        List<SnapshotLine> wrapped = new ArrayList<>();
        for (SnapshotLine line : hoveredLines) wrap(line, metrics, maximumWidth, wrapped);
        return wrapped;
    }

    private static SnapshotLine snapshotLine(FormattedText text) {
        int color = 0xFFFDFDFD;
        if (text instanceof Component component && component.getStyle().getColor() != null) {
            color = 0xFF000000 | component.getStyle().getColor().getValue();
        }

        return new SnapshotLine(text.getString(), color);
    }

    private static Font displayFont() {
        String text = String.join("\n", hoveredLines.stream().map(SnapshotLine::text).toList());
        Font fallback = new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE);
        if (fallback.canDisplayUpTo(text) < 0) return fallback;
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            Font candidate = new Font(family, Font.PLAIN, FONT_SIZE);
            if (candidate.canDisplayUpTo(text) < 0) return candidate;
        }
        return fallback;
    }

    private static void wrap(SnapshotLine source, FontMetrics metrics, int maximumWidth, List<SnapshotLine> output) {
        String text = source.text();
        if (text.isEmpty()) {
            output.add(source);
            return;
        }
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int next = offset + Character.charCount(codePoint);
            if (codePoint == '\n') {
                output.add(new SnapshotLine(line.toString(), source.color()));
                line.setLength(0);
                width = 0;
                offset = next;
                continue;
            }
            int codePointWidth = metrics.charWidth(codePoint);
            if (width + codePointWidth > maximumWidth && !line.isEmpty()) {
                output.add(new SnapshotLine(line.toString(), source.color()));
                line.setLength(0);
                width = 0;
            }
            line.appendCodePoint(codePoint);
            width += codePointWidth;
            offset = next;
        }

        if (!line.isEmpty()) output.add(new SnapshotLine(line.toString(), source.color()));
    }

    public record ExportResult(List<Path> files, int characters) {}

    private record SnapshotLayout(int width, int height, int columns, int columnWidth) {}

    private record SnapshotLine(String text, int color) {}

}