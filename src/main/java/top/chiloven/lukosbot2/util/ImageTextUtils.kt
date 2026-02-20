package top.chiloven.lukosbot2.util

import org.apache.logging.log4j.LogManager
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.util.*

object ImageTextUtils {
    private val log = LogManager.getLogger(ImageTextUtils::class.java)

    data class Run(val text: String, val font: Font)

    class GlyphRunCache {
        private val displayable = HashMap<FontKey, BitSet>()
        private val computed = HashMap<FontKey, BitSet>()

        fun canDisplay(f: Font?, c: Char): Boolean {
            if (f == null) return false
            val key = FontKey(f.family, f.style, f.size)
            val yes = displayable.getOrPut(key) { BitSet(65536) }
            val done = computed.getOrPut(key) { BitSet(65536) }
            val idx = c.code
            if (done[idx]) return yes[idx]
            val ok = f.canDisplay(c)
            if (ok) yes.set(idx)
            done.set(idx)
            return ok
        }

        private data class FontKey(val family: String, val style: Int, val size: Int)
    }

    object FontFallback {
        private val NORMAL_FAMILIES = listOf(
            "Source Han Sans SC", "Microsoft Yahei UI", "SimSun", Font.SANS_SERIF, "SansSerif"
        )
        private val CODE_FAMILIES = listOf(
            "Cascadia Code", "Consolas", Font.MONOSPACED, "Monospaced"
        )
        private val LOGICAL_FAMILIES = setOf("Dialog", "DialogInput", "SansSerif", "Serif", "Monospaced")

        fun resolveNormal(style: Int, size: Int, provided: Font?): Font {
            if (provided == null || isLogicalFamily(provided.family)) {
                return pickFirstInstalled(NORMAL_FAMILIES, style, size)
            }
            if (isFamilyAvailable(provided.family)) return provided.deriveFont(style, size.toFloat())
            return pickFirstInstalled(NORMAL_FAMILIES, style, size)
        }

        fun resolveCode(style: Int, size: Int, provided: Font?): Font {
            if (provided == null || isLogicalFamily(provided.family)) {
                return pickFirstInstalled(CODE_FAMILIES, style, size)
            }
            if (isFamilyAvailable(provided.family)) return provided.deriveFont(style, size.toFloat())
            return pickFirstInstalled(CODE_FAMILIES, style, size)
        }

        private fun pickFirstInstalled(families: List<String>, style: Int, size: Int): Font {
            for (fam in families) {
                if (isFamilyAvailable(fam)) return Font(fam, style, size)
            }
            return Font(Font.SANS_SERIF, style, size)
        }

        private fun isLogicalFamily(family: String?): Boolean {
            if (family.isNullOrBlank()) return true
            if (LOGICAL_FAMILIES.any { it.equals(family, ignoreCase = true) }) return true
            val f = family.trim().lowercase()
            return f.startsWith("dialog") || f.startsWith("sansserif") || f.startsWith("serif") || f.startsWith("monospaced")
        }

        private fun isFamilyAvailable(family: String?): Boolean {
            if (family.isNullOrBlank()) return false
            val target = family.trim()
            val exists = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .any { it.equals(target, ignoreCase = true) }

            if (!exists) log.warn("Font family '{}' not found in available system fonts.", target)
            return exists
        }
    }

    private fun sameFamilyStyleSize(a: Font?, b: Font?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        return a.style == b.style && a.size == b.size && a.family.equals(b.family, ignoreCase = true)
    }

    private fun chooseFontForChar(c: Char, primary: Font, fallback: Font, cache: GlyphRunCache): Font {
        if (cache.canDisplay(primary, c)) return primary
        if (cache.canDisplay(fallback, c)) return fallback
        return primary
    }

    fun splitRuns(text: String, primary: Font, fallback: Font, cache: GlyphRunCache): List<Run> {
        if (text.isEmpty()) return emptyList()
        val runs = ArrayList<Run>()
        val buf = StringBuilder()
        var current: Font? = null

        for (c in text) {
            val chosen = chooseFontForChar(c, primary, fallback, cache)
            if (current == null) {
                current = chosen
                buf.append(c)
                continue
            }
            if (sameFamilyStyleSize(current, chosen)) {
                buf.append(c)
            } else {
                runs.add(Run(buf.toString(), current))
                buf.setLength(0)
                buf.append(c)
                current = chosen
            }
        }
        if (buf.isNotEmpty() && current != null) runs.add(Run(buf.toString(), current))
        return runs
    }

    fun drawStringWithFallback(
        g: Graphics2D,
        text: String,
        x: Int,
        y: Int,
        primary: Font,
        fallback: Font,
        cache: GlyphRunCache
    ) {
        if (text.isEmpty()) return
        val runs = splitRuns(text, primary, fallback, cache)
        var dx = 0
        for (r in runs) {
            g.font = r.font
            val fm = g.getFontMetrics(r.font)
            g.drawString(r.text, x + dx, y)
            dx += fm.stringWidth(r.text)
        }
    }

    fun measureTextRunAware(
        g: Graphics2D,
        text: String,
        primary: Font,
        fallback: Font,
        cache: GlyphRunCache
    ): Int {
        if (text.isEmpty()) return 0
        val runs = splitRuns(text, primary, fallback, cache)
        var w = 0
        for (r in runs) {
            g.font = r.font
            val fm = g.getFontMetrics(r.font)
            w += fm.stringWidth(r.text)
        }
        return w
    }

    fun wrapTextRunAware(
        g: Graphics2D,
        text: String,
        primary: Font,
        fallback: Font,
        cache: GlyphRunCache,
        maxWidth: Int
    ): List<String> {
        val s = text.replace("\t", "    ")
        if (s.isEmpty()) return listOf("")
        if (measureTextRunAware(g, s, primary, fallback, cache) <= maxWidth) return listOf(s)

        val out = ArrayList<String>()
        val line = StringBuilder()
        for (c in s) {
            if (c == '\n') {
                out.add(line.toString())
                line.setLength(0)
                continue
            }
            line.append(c)
            if (measureTextRunAware(g, line.toString(), primary, fallback, cache) > maxWidth) {
                // back off one char
                line.setLength(line.length - 1)
                if (line.isNotEmpty()) {
                    out.add(line.toString())
                    line.setLength(0)
                }
                if (c != ' ') line.append(c)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    fun ellipsizeRunAware(
        g: Graphics2D,
        text: String,
        maxPx: Int,
        primary: Font,
        fallback: Font,
        cache: GlyphRunCache
    ): String {
        if (measureTextRunAware(g, text, primary, fallback, cache) <= maxPx) return text
        val ell = "…"
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val s = text.substring(0, mid) + ell
            if (measureTextRunAware(g, s, primary, fallback, cache) <= maxPx) lo = mid + 1 else hi = mid
        }
        val cut = (lo - 1).coerceAtLeast(0)
        return text.substring(0, cut) + ell
    }

    fun ascent(g: Graphics2D, f: Font): Int {
        g.font = f
        val fm: FontMetrics = g.getFontMetrics(f)
        return fm.ascent
    }

    fun height(g: Graphics2D, f: Font): Int {
        g.font = f
        val fm: FontMetrics = g.getFontMetrics(f)
        return fm.height
    }
}
