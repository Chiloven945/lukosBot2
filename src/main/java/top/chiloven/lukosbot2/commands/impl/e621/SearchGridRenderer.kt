package top.chiloven.lukosbot2.commands.impl.e621

import okhttp3.OkHttpClient
import okhttp3.Request
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.UsageImageUtils
import top.chiloven.lukosbot2.commands.impl.e621.schema.Post
import top.chiloven.lukosbot2.util.ImageTextUtils
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.sqrt

object SearchGridRenderer {

    private val style = UsageImageUtils.ImageStyle().resolveFontFallbacks()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
                val body = resp.body ?: return null
                ImageIO.read(body.bytes().inputStream())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawThumb(g: java.awt.Graphics2D, img: BufferedImage?, x: Int, y: Int, w: Int, h: Int) {
        g.drawRect(x, y, w, h)
        if (img == null) {
            val cache = ImageTextUtils.GlyphRunCache()
            val primary = style.bodyFont
            val fallback = style.bodyFont
            val ascent = ImageTextUtils.ascent(g, primary)
            ImageTextUtils.drawStringWithFallback(g, "no preview", x + 8, y + 8 + ascent, primary, fallback, cache)
            return
        }

        val sw = img.width.toDouble()
        val sh = img.height.toDouble()
        val scale = minOf(w / sw, h / sh)
        val tw = (sw * scale).toInt().coerceAtLeast(1)
        val th = (sh * scale).toInt().coerceAtLeast(1)
        val dx = x + (w - tw) / 2
        val dy = y + (h - th) / 2
        g.drawImage(img, dx, dy, tw, th, null)
    }

    fun render(search: String, page: Int, posts: List<Post>): ByteArray {
        val cache = ImageTextUtils.GlyphRunCache()

        val n = posts.size.coerceAtLeast(1)
        val cols = ceil(sqrt(n.toDouble())).toInt().coerceIn(1, 5)
        val rows = ceil(n / cols.toDouble()).toInt()

        val pad = 14
        val thumb = 220
        val captionH = 54
        val headerH = 44

        val cellW = thumb
        val cellH = thumb + captionH

        val w = pad * 2 + cols * cellW + (cols - 1) * pad
        val h = pad * 2 + headerH + rows * cellH + (rows - 1) * pad

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)

        g.color = Color(20, 20, 20)
        val title = "“$search”的搜索结果（第 $page 页）"
        val titlePrimary = style.titleFont
        val titleFallback = style.bodyFont
        val titleAscent = ImageTextUtils.ascent(g, titlePrimary)
        ImageTextUtils.drawStringWithFallback(g, title, pad, pad + titleAscent + 6, titlePrimary, titleFallback, cache)

        val baseY = pad + headerH

        val bodyPrimary = style.bodyFont
        val bodyFallback = style.bodyFont

        for (i in posts.indices) {
            val post = posts[i]
            val r = i / cols
            val c = i % cols

            val x = pad + c * (cellW + pad)
            val y = baseY + r * (cellH + pad)

            val img = loadImage(post.preview.url ?: post.sample.url ?: post.file.url)

            g.color = Color(200, 200, 200)
            drawThumb(g, img, x, y, cellW, thumb)

            val capY = y + thumb
            g.color = Color(245, 245, 245)
            g.fillRect(x, capY, cellW, captionH)
            g.color = Color(160, 160, 160)
            g.drawRect(x, capY, cellW, captionH)

            g.color = Color(20, 20, 20)

            val idLine = "#${post.id}"
            val idAscent = ImageTextUtils.ascent(g, bodyPrimary)
            ImageTextUtils.drawStringWithFallback(g, idLine, x + 8, capY + 20, bodyPrimary, bodyFallback, cache)

            val author = post.tags.getStringArtist().ifBlank { "(unknown)" }
            val authorFit = ImageTextUtils.ellipsizeRunAware(
                g = g,
                text = author,
                maxPx = cellW - 16,
                primary = bodyPrimary,
                fallback = bodyFallback,
                cache = cache
            )
            ImageTextUtils.drawStringWithFallback(g, authorFit, x + 8, capY + 40, bodyPrimary, bodyFallback, cache)
        }

        g.dispose()

        val bos = ByteArrayOutputStream()
        ImageIO.write(out, "png", bos)
        return bos.toByteArray()
    }
}
