package top.chiloven.lukosbot2.util;

import lombok.extern.log4j.Log4j2;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.commands.UsageTextRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;

/**
 * Render usage text to PNG image (for platforms where text is too long).
 *
 * <p>This renderer supports real glyph fallback:
 * if a character cannot be displayed by the preferred font (e.g. Cascadia Code lacks CJK glyphs),
 * the renderer will split the line into runs and draw each run with a font that can display it.
 *
 * <p>Without run-based fallback, Java2D may render missing glyphs as tofu (□) instead of falling
 * back automatically.</p>
 */
@Log4j2
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

        style = style.resolveFontFallbacks();

        String safeBase = sanitizeFilenameBase(filenameBase);
        String filename = safeBase + ".png";

        int contentMaxWidth = style.maxWidth - style.padding * 2;
        if (contentMaxWidth < 100) contentMaxWidth = 100;

        // A tiny graphics context for measurement
        BufferedImage tmp = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g0 = tmp.createGraphics();
        g0.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g0.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fallback fonts used to cover missing glyphs
        Font normalFallback = style.bodyFont;
        Font codeFallback = style.bodyFont; // when CODE font misses glyph, fall back to normal font chain

        GlyphRunCache cache = new GlyphRunCache();

        List<DrawLine> drawLines = new ArrayList<>();
        int maxLineWidth = 0;
        int totalHeight = style.padding * 2;

        for (UsageTextRenderer.RenderedLine l : (lines == null ? List.<UsageTextRenderer.RenderedLine>of() : lines)) {
            UsageTextRenderer.LineKind kind = l.kind();
            String text = l.plain();

            if (kind == UsageTextRenderer.LineKind.BLANK || text == null || text.isBlank()) {
                FontMetrics fm = g0.getFontMetrics(style.bodyFont);
                int h = Math.round(fm.getHeight() * style.lineSpacing * 0.6f);
                drawLines.add(DrawLine.blank(h));
                totalHeight += h;
                continue;
            }

            Font primary = fontFor(kind, style);

            // Wrap using run-aware width calculation (important for glyph fallback correctness)
            List<String> wrapped = wrapTextRunAware(text, primary, kind == UsageTextRenderer.LineKind.CODE ? codeFallback : normalFallback,
                    g0, cache, contentMaxWidth);

            for (String part : wrapped) {
                int w = measureTextRunAware(part, primary, kind == UsageTextRenderer.LineKind.CODE ? codeFallback : normalFallback,
                        g0, cache);
                FontMetrics fm = g0.getFontMetrics(primary);
                int h = Math.round(fm.getHeight() * style.lineSpacing);

                drawLines.add(DrawLine.text(kind, part, primary, w, h));
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
            if (dl.blank) {
                y += dl.height;
                continue;
            }

            Font primary = dl.primaryFont;
            UsageTextRenderer.LineKind kind = dl.kind;

            Font fallback = (kind == UsageTextRenderer.LineKind.CODE) ? codeFallback : normalFallback;

            // Baseline positioning based on primary font metrics
            g.setFont(primary);
            FontMetrics fm = g.getFontMetrics(primary);
            y += fm.getAscent();

            // Draw with real glyph fallback (split into runs by displayability)
            drawStringWithFallback(g, dl.text, x, y, primary, fallback, cache);

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

    /**
     * Draw a string with glyph fallback by splitting it into runs.
     *
     * <p>If {@code primary} cannot display some characters, those characters (and their contiguous
     * neighbors with the same font selection) are rendered using {@code fallback}. If even fallback
     * cannot display, we still draw with primary to keep behavior predictable (tofu may appear).</p>
     */
    private static void drawStringWithFallback(Graphics2D g, String text, int x, int y,
                                               Font primary, Font fallback, GlyphRunCache cache) {
        if (text == null || text.isEmpty()) return;

        List<Run> runs = splitRuns(text, primary, fallback, cache);

        int dx = 0;
        for (Run r : runs) {
            Font f = r.font;
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics(f);
            g.drawString(r.text, x + dx, y);
            dx += fm.stringWidth(r.text);
        }
    }

    private static int measureTextRunAware(String text, Font primary, Font fallback,
                                           Graphics2D g, GlyphRunCache cache) {
        if (text == null || text.isEmpty()) return 0;
        List<Run> runs = splitRuns(text, primary, fallback, cache);

        int w = 0;
        for (Run r : runs) {
            g.setFont(r.font);
            FontMetrics fm = g.getFontMetrics(r.font);
            w += fm.stringWidth(r.text);
        }
        return w;
    }

    /**
     * Wrap text by measuring width using run-based fallback.
     *
     * <p>This avoids the bug where wrapping uses primary font width even when glyphs are rendered
     * with fallback font at draw time, which would cause overflow or premature wrapping.</p>
     */
    private static List<String> wrapTextRunAware(String text, Font primary, Font fallback,
                                                 Graphics2D g, GlyphRunCache cache, int maxWidth) {
        String s = text == null ? "" : text.replace("\t", "    ");
        if (s.isEmpty()) return List.of("");

        if (measureTextRunAware(s, primary, fallback, g, cache) <= maxWidth) {
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

            if (measureTextRunAware(line.toString(), primary, fallback, g, cache) > maxWidth) {
                // back off one char
                line.setLength(line.length() - 1);

                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }

                // start new line with current char (unless space)
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

    /**
     * Split text into runs where each run uses a font that can display all its characters.
     */
    private static List<Run> splitRuns(String s, Font primary, Font fallback, GlyphRunCache cache) {
        if (s == null || s.isEmpty()) return List.of();

        List<Run> runs = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Font current = null;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Font chosen = chooseFontForChar(c, primary, fallback, cache);

            if (current == null) {
                current = chosen;
                buf.append(c);
                continue;
            }

            if (sameFamilyStyleSize(current, chosen)) {
                buf.append(c);
            } else {
                runs.add(new Run(buf.toString(), current));
                buf.setLength(0);
                buf.append(c);
                current = chosen;
            }
        }

        if (!buf.isEmpty() && current != null) {
            runs.add(new Run(buf.toString(), current));
        }

        return runs;
    }

    private static boolean sameFamilyStyleSize(Font a, Font b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getStyle() == b.getStyle()
                && a.getSize() == b.getSize()
                && a.getFamily().equalsIgnoreCase(b.getFamily());
    }

    private static Font chooseFontForChar(char c, Font primary, Font fallback, GlyphRunCache cache) {
        // Prefer primary if it can display
        if (cache.canDisplay(primary, c)) return primary;

        // If primary can't, try fallback
        if (cache.canDisplay(fallback, c)) return fallback;

        // If still can't, return primary (to keep consistent behavior; tofu may appear)
        return primary;
    }

    private static String sanitizeFilenameBase(String base) {
        String s = (base == null || base.isBlank()) ? "usage" : base.trim();
        s = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.length() > 64) s = s.substring(0, 64);
        if (s.isBlank()) s = "usage";
        return s;
    }

    private record Run(String text, Font font) {
    }

    /**
     * Caches per-(font family/style/size, char) displayability to avoid repeated canDisplay calls.
     */
    private static final class GlyphRunCache {
        private final Map<FontKey, BitSet> displayable = new HashMap<>();
        private final Map<FontKey, BitSet> computed = new HashMap<>();

        boolean canDisplay(Font f, char c) {
            if (f == null) return false;
            FontKey key = new FontKey(f.getFamily(), f.getStyle(), f.getSize());
            BitSet bs = displayable.computeIfAbsent(key, k -> new BitSet(65536));

            int idx = c;
            return computeCanDisplay(f, bs, key, idx);
        }

        private boolean computeCanDisplay(Font f, BitSet yes, FontKey key, int idx) {
            BitSet done = computed.computeIfAbsent(key, k -> new BitSet(65536));
            if (done.get(idx)) {
                return yes.get(idx);
            }
            boolean ok = f.canDisplay((char) idx);
            if (ok) yes.set(idx);
            done.set(idx);
            return ok;
        }

        private record FontKey(String family, int style, int size) {
        }
    }

    /**
     * Font family fallback rules:
     * <ul>
     *   <li>Normal text: "Source Han Sans SC" -> "Microsoft Yahei UI" -> "SimSun" -> logical "SansSerif"</li>
     *   <li>Command/code text: "Cascadia Code" -> "Consolas" -> logical "Monospaced"
     *       (but glyph fallback will use the normal text font for missing characters)</li>
     * </ul>
     *
     * <p>Important: Java "logical fonts" (SansSerif/Monospaced/Dialog/...) are always available.
     * If we treat them as a "preferred" font, fallback selection will never happen.
     * Therefore, when the provided font is a logical font, we ignore it and choose from the fallback chain.</p>
     */
    private static final class FontFallback {
        private static final List<String> NORMAL_FAMILIES =
                List.of("Source Han Sans SC", "Microsoft Yahei UI", "SimSun", Font.SANS_SERIF, "SansSerif");

        private static final List<String> CODE_FAMILIES =
                List.of("Cascadia Code", "Consolas", Font.MONOSPACED, "Monospaced");

        private static final List<String> LOGICAL_FAMILIES =
                List.of("Dialog", "DialogInput", "SansSerif", "Serif", "Monospaced");

        private FontFallback() {
        }

        static Font resolveNormal(int style, int size, Font provided) {
            if (provided == null || isLogicalFamily(provided.getFamily())) {
                return pickFirstInstalled(NORMAL_FAMILIES, style, size);
            }
            if (isFamilyAvailable(provided.getFamily())) {
                return provided.deriveFont(style, (float) size);
            }
            return pickFirstInstalled(NORMAL_FAMILIES, style, size);
        }

        static Font resolveCode(int style, int size, Font provided) {
            if (provided == null || isLogicalFamily(provided.getFamily())) {
                return pickFirstInstalled(CODE_FAMILIES, style, size);
            }
            if (isFamilyAvailable(provided.getFamily())) {
                return provided.deriveFont(style, (float) size);
            }
            return pickFirstInstalled(CODE_FAMILIES, style, size);
        }

        private static Font pickFirstInstalled(List<String> families, int style, int size) {
            for (String fam : families) {
                if (isFamilyAvailable(fam)) {
                    return new Font(fam, style, size);
                }
            }
            return new Font(Font.SANS_SERIF, style, size);
        }

        private static boolean isLogicalFamily(String family) {
            if (family == null) return true;
            for (String lf : LOGICAL_FAMILIES) {
                if (lf.equalsIgnoreCase(family)) return true;
            }
            String f = family.trim().toLowerCase();
            return f.startsWith("dialog") || f.startsWith("sansserif") || f.startsWith("serif") || f.startsWith("monospaced");
        }

        private static boolean isFamilyAvailable(String family) {
            if (family == null || family.isBlank()) return false;

            String target = family.trim();
            boolean exists = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                    .anyMatch(target::equalsIgnoreCase);
            if (!exists) {
                log.warn("Font family '{}' not found in available system fonts.", target);
            }
            return exists;
        }
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

        /**
         * Resolve all font fields with the required family fallback chains.
         *
         * <p>Note: real glyph fallback for mixed scripts is handled at draw time by splitting runs.</p>
         */
        public ImageStyle resolveFontFallbacks() {
            Font resolvedTitle = FontFallback.resolveNormal(titleFont.getStyle(), titleFont.getSize(), titleFont);
            Font resolvedHeading = FontFallback.resolveNormal(headingFont.getStyle(), headingFont.getSize(), headingFont);
            Font resolvedBody = FontFallback.resolveNormal(bodyFont.getStyle(), bodyFont.getSize(), bodyFont);
            Font resolvedCode = FontFallback.resolveCode(codeFont.getStyle(), codeFont.getSize(), codeFont);

            return new ImageStyle(
                    maxWidth,
                    minWidth,
                    padding,
                    lineSpacing,
                    background,
                    foreground,
                    resolvedTitle,
                    resolvedHeading,
                    resolvedBody,
                    resolvedCode
            );
        }
    }

    public record RenderedImage(String filename, byte[] bytes, String mime) {
    }

    private record DrawLine(boolean blank, UsageTextRenderer.LineKind kind, String text, Font primaryFont, int width,
                            int height) {

        static DrawLine blank(int height) {
            return new DrawLine(true, UsageTextRenderer.LineKind.BLANK, "", null, 0, height);
        }

        static DrawLine text(UsageTextRenderer.LineKind kind, String text, Font font, int width, int height) {
            return new DrawLine(false, kind, text, font, width, height);
        }
    }
}
