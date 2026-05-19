package top.chiloven.lukosbot2.util

import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.*
import kotlin.math.max

/**
 * Shared Java2D primitives and theme tokens for modern card-like bot images.
 *
 * Keep generated image colors centralized here. Renderers should use [Palette]
 * instead of declaring their own light/dark colors.
 */
object ModernImageDraw {

    enum class ThemeMode {
        LIGHT,
        DARK;

        companion object {

            fun parse(raw: String?): ThemeMode {
                return when (raw?.trim()?.lowercase(Locale.ROOT)) {
                    "dark", "night", "black" -> DARK
                    else -> LIGHT
                }
            }
        }
    }

    data class Palette(
        val bgTop: Color,
        val bgBottom: Color,
        val surface: Color,
        val surfaceSoft: Color,
        val border: Color,
        val text: Color,
        val muted: Color,
        val subtle: Color,
        val accent: Color,
        val accentSoft: Color,
        val codeBg: Color,
        val codeText: Color,

        // Extended semantic tokens used by image renderers.
        val secondaryText: Color,
        val itemBorder: Color,
        val pillBg: Color,
        val chipBg: Color,
        val accentChipBg: Color,
        val itemBg: Color,
        val codeChipBg: Color,

        val ratingExplicitFg: Color,
        val ratingExplicitBg: Color,
        val ratingSafeFg: Color,
        val ratingSafeBg: Color,
        val ratingQuestionableFg: Color,
        val ratingQuestionableBg: Color,
        val ratingUnknownFg: Color,
        val ratingUnknownBg: Color,
    ) {

        constructor() : this(
            bgTop = Color(248, 250, 252),
            bgBottom = Color(241, 245, 249),
            surface = Color.WHITE,
            surfaceSoft = Color(248, 250, 252),
            border = Color(226, 232, 240),
            text = Color(15, 23, 42),
            muted = Color(100, 116, 139),
            subtle = Color(148, 163, 184),
            accent = Color(99, 102, 241),
            accentSoft = Color(238, 242, 255),
            codeBg = Color(15, 23, 42),
            codeText = Color(226, 232, 240),
            secondaryText = Color(51, 65, 85),
            itemBorder = Color(203, 213, 225),
            pillBg = Color(224, 231, 255),
            chipBg = Color(241, 245, 249),
            accentChipBg = Color(238, 242, 255),
            itemBg = Color(250, 250, 252),
            codeChipBg = Color(30, 41, 59),
            ratingExplicitFg = Color(220, 38, 38),
            ratingExplicitBg = Color(254, 226, 226),
            ratingSafeFg = Color(22, 163, 74),
            ratingSafeBg = Color(220, 252, 231),
            ratingQuestionableFg = Color(202, 138, 4),
            ratingQuestionableBg = Color(254, 249, 195),
            ratingUnknownFg = Color(100, 116, 139),
            ratingUnknownBg = Color(248, 250, 252),
        )
    }

    @JvmStatic
    fun configuredThemeMode(): ThemeMode = ThemeMode.parse(configuredThemeRaw())

    @JvmStatic
    fun defaultPalette(): Palette = paletteFor(configuredThemeMode())

    @JvmStatic
    fun paletteFor(mode: ThemeMode): Palette {
        return when (mode) {
            ThemeMode.DARK -> darkPalette()
            ThemeMode.LIGHT -> lightPalette()
        }
    }

    @JvmStatic
    fun lightPalette(): Palette {
        return Palette(
            bgTop = Color(248, 250, 252),
            bgBottom = Color(241, 245, 249),
            surface = Color.WHITE,
            surfaceSoft = Color(248, 250, 252),
            border = Color(226, 232, 240),
            text = Color(15, 23, 42),
            muted = Color(100, 116, 139),
            subtle = Color(148, 163, 184),
            accent = Color(99, 102, 241),
            accentSoft = Color(238, 242, 255),
            codeBg = Color(15, 23, 42),
            codeText = Color(248, 250, 252),
            secondaryText = Color(51, 65, 85),
            itemBorder = Color(203, 213, 225),
            pillBg = Color(224, 231, 255),
            chipBg = Color(241, 245, 249),
            accentChipBg = Color(238, 242, 255),
            itemBg = Color(250, 250, 252),
            codeChipBg = Color(30, 41, 59),
            ratingExplicitFg = Color(220, 38, 38),
            ratingExplicitBg = Color(254, 226, 226),
            ratingSafeFg = Color(22, 163, 74),
            ratingSafeBg = Color(220, 252, 231),
            ratingQuestionableFg = Color(202, 138, 4),
            ratingQuestionableBg = Color(254, 249, 195),
            ratingUnknownFg = Color(100, 116, 139),
            ratingUnknownBg = Color(248, 250, 252),
        )
    }

    @JvmStatic
    fun darkPalette(): Palette {
        return Palette(
            bgTop = Color(15, 23, 42),
            bgBottom = Color(2, 6, 23),
            surface = Color(15, 23, 42),
            surfaceSoft = Color(30, 41, 59),
            border = Color(51, 65, 85),
            text = Color(241, 245, 249),
            muted = Color(203, 213, 225),
            subtle = Color(148, 163, 184),
            accent = Color(129, 140, 248),
            accentSoft = Color(49, 46, 129),
            codeBg = Color(2, 6, 23),
            codeText = Color(241, 245, 249),
            secondaryText = Color(203, 213, 225),
            itemBorder = Color(71, 85, 105),
            pillBg = Color(49, 46, 129),
            chipBg = Color(30, 41, 59),
            accentChipBg = Color(49, 46, 129),
            itemBg = Color(30, 41, 59),
            codeChipBg = Color(2, 6, 23),
            ratingExplicitFg = Color(254, 202, 202),
            ratingExplicitBg = Color(127, 29, 29),
            ratingSafeFg = Color(187, 247, 208),
            ratingSafeBg = Color(22, 101, 52),
            ratingQuestionableFg = Color(254, 240, 138),
            ratingQuestionableBg = Color(113, 63, 18),
            ratingUnknownFg = Color(203, 213, 225),
            ratingUnknownBg = Color(30, 41, 59),
        )
    }

    private fun configuredThemeRaw(): String? {
        return runCatching {
            val props = SpringBeans.getBean(AppProperties::class.java)
            props.image.theme.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun quality(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    fun background(g: Graphics2D, width: Int, height: Int, palette: Palette = defaultPalette()) {
        g.paint = GradientPaint(0f, 0f, palette.bgTop, 0f, height.toFloat(), palette.bgBottom)
        g.fillRect(0, 0, width, height)
        g.paint = null
    }

    fun softShadow(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, radius: Int) {
        val oldComposite = g.composite
        for (i in 12 downTo 1) {
            val alpha = 0.012f + i * 0.003f
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceAtMost(0.06f))
            g.color = Color(15, 23, 42)
            g.fillRoundRect(
                x - i / 2,
                y + i,
                width + i,
                height + i,
                radius + i,
                radius + i
            )
        }
        g.composite = oldComposite
    }

    fun card(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, radius: Int, palette: Palette = defaultPalette()) {
        softShadow(g, x, y, width, height, radius)
        g.color = palette.surface
        g.fillRoundRect(x, y, width, height, radius, radius)
        roundedBorder(g, x, y, width, height, radius, palette.border)
    }

    fun roundedBorder(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        color: Color,
        strokeWidth: Float = 1f
    ) {
        val oldStroke = g.stroke
        g.stroke = BasicStroke(strokeWidth)
        g.color = color
        g.drawRoundRect(x, y, width, height, radius, radius)
        g.stroke = oldStroke
    }

    fun pill(g: Graphics2D, text: String, x: Int, y: Int, font: Font, fg: Color, bg: Color): Int {
        val cache = ImageTextUtils.GlyphRunCache()
        val fm = g.getFontMetrics(font)
        val padX = 12
        val padY = 5
        val width = ImageTextUtils.measureTextRunAware(g, text, font, font, cache) + padX * 2
        val height = fm.height + padY * 2 - 4

        g.color = bg
        g.fillRoundRect(x, y, width, height, height, height)

        g.color = fg
        ImageTextUtils.drawStringWithFallback(
            g,
            text,
            x + padX,
            y + padY + fm.ascent - 2,
            font,
            font,
            cache
        )
        return width
    }

    fun imageCoverRounded(
        g: Graphics2D,
        img: BufferedImage,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val oldClip: Shape? = g.clip
        g.clip = RoundRectangle2D.Float(
            x.toFloat(),
            y.toFloat(),
            width.toFloat(),
            height.toFloat(),
            radius.toFloat(),
            radius.toFloat()
        )

        val scale = max(width / img.width.toDouble(), height / img.height.toDouble())
        val targetW = (img.width * scale).toInt().coerceAtLeast(1)
        val targetH = (img.height * scale).toInt().coerceAtLeast(1)
        val dx = x + (width - targetW) / 2
        val dy = y + (height - targetH) / 2

        g.drawImage(img, dx, dy, targetW, targetH, null)
        g.clip = oldClip
    }

}
