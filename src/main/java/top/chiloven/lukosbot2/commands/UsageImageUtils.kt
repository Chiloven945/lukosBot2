package top.chiloven.lukosbot2.commands

import top.chiloven.lukosbot2.util.ImageTextUtils
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object UsageImageUtils {

    @JvmStatic
    fun renderUsagePng(
        filenameBase: String,
        node: UsageNode,
        options: UsageTextRenderer.Options,
        style: ImageStyle
    ): RenderedImage {
        Objects.requireNonNull(style, "style")
        val result = UsageTextRenderer.render(node, options)
        return renderLinesPng(filenameBase, result.lines(), style)
    }

    @JvmStatic
    fun renderLinesPng(
        filenameBase: String,
        lines: List<UsageTextRenderer.RenderedLine>?,
        style0: ImageStyle
    ): RenderedImage {
        val style = style0.resolveFontFallbacks()
        val safeBase = sanitizeFilenameBase(filenameBase)
        val filename = "$safeBase.png"

        var contentMaxWidth = style.maxWidth - style.padding * 2
        if (contentMaxWidth < 100) contentMaxWidth = 100

        // measurement context
        val tmp = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val g0 = tmp.createGraphics()
        g0.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g0.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val cache = ImageTextUtils.GlyphRunCache()
        val drawLines = ArrayList<DrawLine>()
        var maxLineWidth = 0
        var totalHeight = style.padding * 2

        val normalFallback = style.bodyFont
        val codeFallback = style.bodyFont

        for (l in lines ?: emptyList()) {
            val kind = l.kind()
            val text = l.plain()

            if (kind == UsageTextRenderer.LineKind.BLANK || text.isNullOrBlank()) {
                val h = round(ImageTextUtils.height(g0, style.bodyFont) * style.lineSpacing * 0.6f).toInt()
                drawLines.add(DrawLine.blank(h))
                totalHeight += h
                continue
            }

            val primary = fontFor(kind, style)
            val fallback = if (kind == UsageTextRenderer.LineKind.CODE) codeFallback else normalFallback

            val wrapped = ImageTextUtils.wrapTextRunAware(g0, text, primary, fallback, cache, contentMaxWidth)
            for (part in wrapped) {
                val w = ImageTextUtils.measureTextRunAware(g0, part, primary, fallback, cache)
                val h = round(ImageTextUtils.height(g0, primary) * style.lineSpacing).toInt()
                drawLines.add(DrawLine.text(kind, part, primary, w, h))
                totalHeight += h
                if (w > maxLineWidth) maxLineWidth = w
            }
        }
        g0.dispose()

        val width = min(style.maxWidth, max(style.minWidth, maxLineWidth + style.padding * 2))
        val height = max(120, totalHeight)

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = style.background
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = style.foreground

        val x = style.padding
        var y = style.padding

        for (dl in drawLines) {
            if (dl.blank) {
                y += dl.height
                continue
            }

            val primary = dl.primaryFont!!
            val kind = dl.kind
            val fallback = if (kind == UsageTextRenderer.LineKind.CODE) codeFallback else normalFallback

            val ascent = ImageTextUtils.ascent(g, primary)
            y += ascent

            ImageTextUtils.drawStringWithFallback(g, dl.text, x, y, primary, fallback, cache)

            y += (dl.height - ascent)
        }

        g.dispose()

        try {
            val bos = ByteArrayOutputStream()
            ImageIO.write(img, "png", bos)
            return RenderedImage(filename, bos.toByteArray(), "image/png")
        } catch (e: Exception) {
            throw RuntimeException("Render usage PNG failed: ${e.message}", e)
        }
    }

    private fun fontFor(kind: UsageTextRenderer.LineKind, style: ImageStyle): Font =
        when (kind) {
            UsageTextRenderer.LineKind.TITLE -> style.titleFont
            UsageTextRenderer.LineKind.HEADING -> style.headingFont
            UsageTextRenderer.LineKind.CODE -> style.codeFont
            UsageTextRenderer.LineKind.TEXT, UsageTextRenderer.LineKind.BLANK -> style.bodyFont
        }

    private fun sanitizeFilenameBase(base: String?): String {
        var s = (base?.takeIf { it.isNotBlank() } ?: "usage").trim()
        s = s.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (s.length > 64) s = s.substring(0, 64)
        if (s.isBlank()) s = "usage"
        return s
    }

    data class ImageStyle(
        val maxWidth: Int = 900,
        val minWidth: Int = 420,
        val padding: Int = 20,
        val lineSpacing: Float = 1.25f,
        val background: Color = Color.WHITE,
        val foreground: Color = Color.BLACK,
        val titleFont: Font = Font(Font.SANS_SERIF, Font.BOLD, 20),
        val headingFont: Font = Font(Font.SANS_SERIF, Font.BOLD, 16),
        val bodyFont: Font = Font(Font.SANS_SERIF, Font.PLAIN, 14),
        val codeFont: Font = Font(Font.MONOSPACED, Font.PLAIN, 14),
    ) {
        companion object {
            @JvmStatic
            fun defaults(): ImageStyle = ImageStyle()
        }

        fun resolveFontFallbacks(): ImageStyle {
            val resolvedTitle = ImageTextUtils.FontFallback.resolveNormal(titleFont.style, titleFont.size, titleFont)
            val resolvedHeading =
                ImageTextUtils.FontFallback.resolveNormal(headingFont.style, headingFont.size, headingFont)
            val resolvedBody = ImageTextUtils.FontFallback.resolveNormal(bodyFont.style, bodyFont.size, bodyFont)
            val resolvedCode = ImageTextUtils.FontFallback.resolveCode(codeFont.style, codeFont.size, codeFont)
            return copy(
                titleFont = resolvedTitle,
                headingFont = resolvedHeading,
                bodyFont = resolvedBody,
                codeFont = resolvedCode
            )
        }
    }

    data class RenderedImage(val filename: String, val bytes: ByteArray, val mime: String)

    private data class DrawLine(
        val blank: Boolean,
        val kind: UsageTextRenderer.LineKind,
        val text: String,
        val primaryFont: Font?,
        val width: Int,
        val height: Int
    ) {
        companion object {
            fun blank(height: Int) = DrawLine(true, UsageTextRenderer.LineKind.BLANK, "", null, 0, height)
            fun text(kind: UsageTextRenderer.LineKind, text: String, font: Font, width: Int, height: Int) =
                DrawLine(false, kind, text, font, width, height)
        }
    }
}