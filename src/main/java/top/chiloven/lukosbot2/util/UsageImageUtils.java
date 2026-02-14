package top.chiloven.lukosbot2.util;

import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.commands.UsageTextRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Render usage text to PNG image (for platforms where text is too long).
 */
public final class UsageImageUtils {

    private UsageImageUtils() {
    }

    /**
     * Render a {@link UsageNode} into PNG.
     *
     * @param filenameBase ascii-only base filename (without extension)
     */
    public static RenderedImage renderUsagePng(String filenameBase, UsageNode node, UsageTextRenderer.Options options, ImageStyle style) {
        Objects.requireNonNull(style, "style");
        UsageTextRenderer.Result result = UsageTextRenderer.render(node, options);
        return renderLinesPng(filenameBase, result.lines(), style);
    }

    public static RenderedImage renderLinesPng(String filenameBase, List<UsageTextRenderer.RenderedLine> lines, ImageStyle style) {
        Objects.requireNonNull(style, "style");
        String safeBase = sanitizeFilenameBase(filenameBase);
        String filename = safeBase + ".png";

        List<DrawLine> drawLines = new ArrayList<>();
        int contentMaxWidth = style.maxWidth - style.padding * 2;
        if (contentMaxWidth < 100) contentMaxWidth = 100;

        BufferedImage tmp = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g0 = tmp.createGraphics();
        g0.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g0.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int maxLineWidth = 0;
        int totalHeight = style.padding * 2;

        for (UsageTextRenderer.RenderedLine l : (lines == null ? List.<UsageTextRenderer.RenderedLine>of() : lines)) {
            UsageTextRenderer.LineKind kind = l.kind();
            String text = l.plain();

            if (kind == UsageTextRenderer.LineKind.BLANK || text == null || text.isBlank()) {
                FontMetrics fm = g0.getFontMetrics(style.bodyFont);
                int h = Math.round(fm.getHeight() * style.lineSpacing * 0.6f);
                drawLines.add(new DrawLine(UsageTextRenderer.LineKind.BLANK, "", style.bodyFont, 0, h));
                totalHeight += h;
                continue;
            }

            Font font = fontFor(kind, style);
            FontMetrics fm = g0.getFontMetrics(font);
            List<String> wrapped = wrapText(text, fm, contentMaxWidth);

            for (String part : wrapped) {
                int w = fm.stringWidth(part);
                int h = Math.round(fm.getHeight() * style.lineSpacing);

                drawLines.add(new DrawLine(kind, part, font, w, h));
                totalHeight += h;
                maxLineWidth = Math.max(maxLineWidth, w);
            }
        }

        g0.dispose();

        int width = Math.min(style.maxWidth, Math.max(style.minWidth, maxLineWidth + style.padding * 2));
        int height = Math.max(120, totalHeight);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(style.background);
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(style.foreground);

        int x = style.padding;
        int y = style.padding;

        for (DrawLine dl : drawLines) {
            if (dl.kind == UsageTextRenderer.LineKind.BLANK) {
                y += dl.height;
                continue;
            }
            g.setFont(dl.font);
            FontMetrics fm = g.getFontMetrics(dl.font);
            y += fm.getAscent();
            g.drawString(dl.text, x, y);
            y += (dl.height - fm.getAscent());
        }

        g.dispose();

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return new RenderedImage(filename, bos.toByteArray(), "image/png");
        } catch (Exception e) {
            throw new RuntimeException("Render usage PNG failed: " + e.getMessage(), e);
        }
    }

    private static Font fontFor(UsageTextRenderer.LineKind kind, ImageStyle style) {
        return switch (kind) {
            case TITLE -> style.titleFont;
            case HEADING -> style.headingFont;
            case CODE -> style.codeFont;
            case TEXT, BLANK -> style.bodyFont;
        };
    }

    private static List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        String s = text == null ? "" : text.replace("\t", "    ");
        if (s.isEmpty()) return List.of("");

        if (fm.stringWidth(s) <= maxWidth) {
            return List.of(s);
        }

        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '\n') {
                out.add(line.toString());
                line.setLength(0);
                continue;
            }

            line.append(c);

            if (fm.stringWidth(line.toString()) > maxWidth) {
                line.setLength(line.length() - 1);

                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }

                if (c != ' ') {
                    line.append(c);
                }
            }
        }

        if (!line.isEmpty()) {
            out.add(line.toString());
        }

        return out;
    }

    // -------------------- internal helpers --------------------

    private static String sanitizeFilenameBase(String base) {
        String s = (base == null || base.isBlank()) ? "usage" : base.trim();
        // ascii-only, replace unsafe chars
        s = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.length() > 64) s = s.substring(0, 64);
        if (s.isBlank()) s = "usage";
        return s;
    }

    public record ImageStyle(
            int maxWidth,
            int minWidth,
            int padding,
            float lineSpacing,
            Color background,
            Color foreground,
            Font titleFont,
            Font headingFont,
            Font bodyFont,
            Font codeFont
    ) {
        public ImageStyle {
            maxWidth = Math.max(320, maxWidth);
            minWidth = Math.max(240, minWidth);
            padding = Math.max(0, padding);
            lineSpacing = Math.max(1.0f, lineSpacing);

            background = background == null ? Color.WHITE : background;
            foreground = foreground == null ? Color.BLACK : foreground;

            titleFont = titleFont == null ? new Font(Font.SANS_SERIF, Font.BOLD, 20) : titleFont;
            headingFont = headingFont == null ? new Font(Font.SANS_SERIF, Font.BOLD, 16) : headingFont;
            bodyFont = bodyFont == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : bodyFont;
            codeFont = codeFont == null ? new Font(Font.MONOSPACED, Font.PLAIN, 14) : codeFont;
        }

        public static ImageStyle defaults() {
            return new ImageStyle(
                    900,
                    420,
                    20,
                    1.25f,
                    Color.WHITE,
                    Color.BLACK,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record RenderedImage(String filename, byte[] bytes, String mime) {
    }

    private record DrawLine(UsageTextRenderer.LineKind kind, String text, Font font, int width, int height) {
    }
}
