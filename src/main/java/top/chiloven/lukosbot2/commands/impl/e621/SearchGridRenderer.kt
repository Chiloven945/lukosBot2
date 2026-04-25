package top.chiloven.lukosbot2.commands.impl.e621

import okhttp3.OkHttpClient
import okhttp3.Request
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.UsageImageUtils
import top.chiloven.lukosbot2.commands.impl.e621.schema.Post
import top.chiloven.lukosbot2.util.ImageTextUtils
import top.chiloven.lukosbot2.util.ModernImageDraw
import top.chiloven.lukosbot2.util.OkHttpUtils
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.sqrt

object SearchGridRenderer {

    private data class BadgeColors(val fg: Color, val bg: Color)

    private val style: UsageImageUtils.ImageStyle
        get() = UsageImageUtils.ImageStyle.defaults().resolveFontFallbacks()

    private val palette: ModernImageDraw.Palette
        get() = style.palette

    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = 8000,
        callTimeoutMs = 12000,
        followRedirects = true,
        followSslRedirects = true
    )

    val http: OkHttpClient
        get() = clientCache.client

    private fun loadImage(url: String?): BufferedImage? {
        if (url.isNullOrBlank()) return null
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", Constants.UA)
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body
                ImageIO.read(body.bytes().inputStream())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun render(search: String, page: Int, posts: List<Post>): ByteArray {
        val cache = ImageTextUtils.GlyphRunCache()

        val n = posts.size.coerceAtLeast(1)
        val cols = ceil(sqrt(n.toDouble())).toInt().coerceIn(1, 5)
        val rows = ceil(n / cols.toDouble()).toInt()

        val pad = 28
        val gap = 18
        val thumb = 220
        val captionH = 84
        val headerH = 78
        val cardPad = 10
        val cardRadius = 22
        val imageRadius = 18

        val cellW = thumb + cardPad * 2
        val cellH = thumb + captionH + cardPad * 2

        val w = pad * 2 + cols * cellW + (cols - 1) * gap
        val h = pad * 2 + headerH + rows * cellH + (rows - 1) * gap

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        ModernImageDraw.quality(g)
        ModernImageDraw.background(g, w, h, palette)

        drawHeader(g, search, page, posts.size, pad, pad, w - pad * 2, cache)

        val baseY = pad + headerH

        if (posts.isEmpty()) {
            drawEmptyState(g, pad, baseY, cellW, cellH, cache)
        } else {
            for (i in posts.indices) {
                val post = posts[i]
                val r = i / cols
                val c = i % cols

                val x = pad + c * (cellW + gap)
                val y = baseY + r * (cellH + gap)

                drawPostCard(
                    g = g,
                    post = post,
                    x = x,
                    y = y,
                    cellW = cellW,
                    cellH = cellH,
                    thumb = thumb,
                    cardPad = cardPad,
                    cardRadius = cardRadius,
                    imageRadius = imageRadius,
                    cache = cache
                )
            }
        }

        g.dispose()

        val bos = ByteArrayOutputStream()
        ImageIO.write(out, "png", bos)
        return bos.toByteArray()
    }

    private fun drawHeader(
        g: Graphics2D,
        search: String,
        page: Int,
        total: Int,
        x: Int,
        y: Int,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache
    ) {
        val titlePrimary = style.titleFont
        val titleFallback = style.bodyFont
        val bodyPrimary = style.bodyFont
        val bodyFallback = style.bodyFont

        g.color = palette.text
        val title = "搜索结果"
        ImageTextUtils.drawStringWithFallback(
            g,
            title,
            x,
            y + ImageTextUtils.ascent(g, titlePrimary),
            titlePrimary,
            titleFallback,
            cache
        )

        val query = search.ifBlank { "全部" }
        val subtitleRaw = "“$query” · 第 $page 页 · $total 个结果"
        val subtitle = ImageTextUtils.ellipsizeRunAware(
            g,
            subtitleRaw,
            width,
            bodyPrimary,
            bodyFallback,
            cache
        )
        g.color = palette.muted
        ImageTextUtils.drawStringWithFallback(
            g,
            subtitle,
            x,
            y + 34 + ImageTextUtils.ascent(g, bodyPrimary),
            bodyPrimary,
            bodyFallback,
            cache
        )
    }

    private fun drawPostCard(
        g: Graphics2D,
        post: Post,
        x: Int,
        y: Int,
        cellW: Int,
        cellH: Int,
        thumb: Int,
        cardPad: Int,
        cardRadius: Int,
        imageRadius: Int,
        cache: ImageTextUtils.GlyphRunCache
    ) {
        ModernImageDraw.card(g, x, y, cellW, cellH, cardRadius, palette)

        val imgX = x + cardPad
        val imgY = y + cardPad
        val img = loadImage(post.preview.url ?: post.sample.url ?: post.file.url)

        if (img == null) {
            drawNoPreview(g, imgX, imgY, thumb, thumb, imageRadius, cache)
        } else {
            ModernImageDraw.imageCoverRounded(g, img, imgX, imgY, thumb, thumb, imageRadius)
            ModernImageDraw.roundedBorder(g, imgX, imgY, thumb, thumb, imageRadius, palette.border)
        }

        drawPostCaption(g, post, imgX, imgY + thumb + 12, thumb, cache)
    }

    private fun drawPostCaption(
        g: Graphics2D,
        post: Post,
        x: Int,
        y: Int,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache
    ) {
        val bodyPrimary = style.bodyFont
        val bodyFallback = style.bodyFont
        val badgeFont = style.bodyFont.deriveFont(Font.BOLD, 13f)
        val authorFont = style.bodyFont.deriveFont(Font.BOLD, 14f)
        val metaFont = style.bodyFont.deriveFont(12.5f)

        var bx = x
        bx += ModernImageDraw.pill(
            g = g,
            text = "#${post.id}",
            x = bx,
            y = y,
            font = badgeFont,
            fg = palette.accent,
            bg = palette.accentSoft
        ) + 8

        val rating = post.rating.uppercase().ifBlank { "?" }
        val ratingColors = ratingBadgeColors(rating)
        ModernImageDraw.pill(
            g = g,
            text = rating,
            x = bx,
            y = y,
            font = badgeFont,
            fg = ratingColors.fg,
            bg = ratingColors.bg
        )

        val author = post.tags.getStringArtist()
            .ifBlank { post.uploaderName }
            .ifBlank { "(unknown artist)" }
        val authorFit = ImageTextUtils.ellipsizeRunAware(
            g = g,
            text = author,
            maxPx = width,
            primary = authorFont,
            fallback = bodyFallback,
            cache = cache
        )
        g.color = palette.text
        ImageTextUtils.drawStringWithFallback(
            g,
            authorFit,
            x,
            y + 40,
            authorFont,
            bodyFallback,
            cache
        )

        val stats = listOf(
            "🗳️ ${post.score.total}",
            "❤️ ${post.favCount}",
            fileSummary(post)
        ).filter { it.isNotBlank() }.joinToString(" · ")
        val statsFit = ImageTextUtils.ellipsizeRunAware(
            g = g,
            text = stats,
            maxPx = width,
            primary = metaFont,
            fallback = bodyPrimary,
            cache = cache
        )
        g.color = palette.muted
        ImageTextUtils.drawStringWithFallback(
            g,
            statsFit,
            x,
            y + 61,
            metaFont,
            bodyFallback,
            cache
        )
    }


    private fun ratingBadgeColors(rating: String): BadgeColors {
        val p = palette
        return when (rating.uppercase()) {
            "E" -> BadgeColors(p.ratingExplicitFg, p.ratingExplicitBg)
            "S" -> BadgeColors(p.ratingSafeFg, p.ratingSafeBg)
            "Q" -> BadgeColors(p.ratingQuestionableFg, p.ratingQuestionableBg)
            else -> BadgeColors(p.ratingUnknownFg, p.ratingUnknownBg)
        }
    }

    private fun drawNoPreview(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        cache: ImageTextUtils.GlyphRunCache
    ) {
        val primary = style.bodyFont
        val fallback = style.bodyFont
        g.color = palette.surfaceSoft
        g.fillRoundRect(x, y, width, height, radius, radius)
        ModernImageDraw.roundedBorder(g, x, y, width, height, radius, palette.border)

        val text = "no preview"
        val textW = ImageTextUtils.measureTextRunAware(g, text, primary, fallback, cache)
        val fontH = ImageTextUtils.height(g, primary)
        val baseline = y + (height - fontH) / 2 + ImageTextUtils.ascent(g, primary)
        g.color = palette.subtle
        ImageTextUtils.drawStringWithFallback(
            g,
            text,
            x + (width - textW) / 2,
            baseline,
            primary,
            fallback,
            cache
        )
    }

    private fun drawEmptyState(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        cache: ImageTextUtils.GlyphRunCache
    ) {
        ModernImageDraw.card(g, x, y, width, height, 22, palette)

        val title = "没有找到结果"
        val desc = "可以换一组关键词或查看下一页。"
        val titleFont = style.headingFont
        val bodyFont = style.bodyFont
        val titleW = ImageTextUtils.measureTextRunAware(g, title, titleFont, bodyFont, cache)
        val descW = ImageTextUtils.measureTextRunAware(g, desc, bodyFont, bodyFont, cache)
        val centerY = y + height / 2

        g.color = palette.text
        ImageTextUtils.drawStringWithFallback(
            g,
            title,
            x + (width - titleW) / 2,
            centerY - 8,
            titleFont,
            bodyFont,
            cache
        )
        g.color = palette.muted
        ImageTextUtils.drawStringWithFallback(
            g,
            desc,
            x + (width - descW) / 2,
            centerY + 22,
            bodyFont,
            bodyFont,
            cache
        )
    }

    private fun fileSummary(post: Post): String {
        val ext = post.file.ext.uppercase().ifBlank { return "" }
        val w = post.file.width
        val h = post.file.height
        return if (w > 0 && h > 0) "$ext ${w}×$h" else ext
    }

}
