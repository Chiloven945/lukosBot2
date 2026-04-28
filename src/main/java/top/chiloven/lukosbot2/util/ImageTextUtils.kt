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
            return canDisplay(f, c.code)
        }

        fun canDisplay(f: Font?, codePoint: Int): Boolean {
            if (f == null) return false
            if (!Character.isValidCodePoint(codePoint)) return false
            val key = FontKey(f.family, f.style, f.size)
            val yes = displayable.getOrPut(key) { BitSet(Character.MAX_CODE_POINT + 1) }
            val done = computed.getOrPut(key) { BitSet(Character.MAX_CODE_POINT + 1) }
            if (done[codePoint]) return yes[codePoint]

            val ok = runCatching { f.canDisplay(codePoint) }.getOrDefault(false)
            if (ok) yes.set(codePoint)
            done.set(codePoint)
            return ok
        }

        fun canDisplayTextElement(f: Font?, element: String): Boolean {
            if (f == null) return false
            var hasVisibleCodePoint = false
            var i = 0
            while (i < element.length) {
                val cp = element.codePointAt(i)
                i += Character.charCount(cp)
                if (isTransparentForFontFallback(cp)) continue
                hasVisibleCodePoint = true
                if (!canDisplay(f, cp)) return false
            }
            return hasVisibleCodePoint
        }

        private data class FontKey(
            val family: String,
            val style: Int,
            val size: Int
        )

    }

    object FontFallback {

        private val NORMAL_FAMILIES = listOf(
            "Source Han Sans SC",
            "Noto Sans CJK SC",
            "Noto Sans SC",
            "Microsoft Yahei UI",
            "Microsoft YaHei",
            "PingFang SC",
            "SimSun",
            Font.SANS_SERIF,
            "SansSerif"
        )

        private val CODE_FAMILIES = listOf(
            "Cascadia Code",
            "JetBrains Mono",
            "Consolas",
            "Menlo",
            "Monaco",
            Font.MONOSPACED,
            "Monospaced"
        )

        private val SYMBOL_FAMILIES = listOf(
            "Segoe UI Emoji",
            "Segoe UI Symbol",
            "Apple Color Emoji",
            "Noto Color Emoji",
            "Noto Emoji",
            "Twitter Color Emoji",
            "Twemoji Mozilla",
            "EmojiOne Color",
            "Noto Sans Symbols 2",
            "Noto Sans Symbols",
            "Symbola",
            "Arial Unicode MS",
            "DejaVu Sans",
            Font.SANS_SERIF,
            "SansSerif"
        )

        private val LOGICAL_FAMILIES = setOf("Dialog", "DialogInput", "SansSerif", "Serif", "Monospaced")

        private val availableFamilies: Set<String> by lazy {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        }

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

        fun candidateFonts(primary: Font, fallback: Font): List<Font> {
            val out = ArrayList<Font>()
            addUnique(out, primary)
            addUnique(out, fallback)

            val style = primary.style
            val size = primary.size
            for (family in SYMBOL_FAMILIES) {
                if (isFamilyAvailable(family) || isLogicalFamily(family)) {
                    addUnique(out, Font(family, style, size))
                }
            }

            // Keep common CJK / sans fallbacks at the end as a safety net for mixed text.
            for (family in NORMAL_FAMILIES) {
                if (isFamilyAvailable(family) || isLogicalFamily(family)) {
                    addUnique(out, Font(family, style, size))
                }
            }

            return out
        }

        private fun addUnique(list: MutableList<Font>, font: Font) {
            if (list.none { sameFamilyStyleSize(it, font) }) list.add(font)
        }

        private fun pickFirstInstalled(families: List<String>, style: Int, size: Int): Font {
            for (fam in families) {
                if (isFamilyAvailable(fam) || isLogicalFamily(fam)) return Font(fam, style, size)
            }
            return Font(Font.SANS_SERIF, style, size)
        }

        private fun isLogicalFamily(family: String?): Boolean {
            if (family.isNullOrBlank()) return true
            if (LOGICAL_FAMILIES.any { it.equals(family, ignoreCase = true) }) return true
            val f = family.trim().lowercase(Locale.ROOT)
            return f.startsWith("dialog") || f.startsWith("sansserif") || f.startsWith("serif") || f.startsWith("monospaced")
        }

        private fun isFamilyAvailable(family: String?): Boolean {
            if (family.isNullOrBlank()) return false
            val target = family.trim()
            if (isLogicalFamily(target)) return true
            val exists = availableFamilies.contains(target.lowercase(Locale.ROOT))
            return exists
        }

    }

    private fun sameFamilyStyleSize(a: Font?, b: Font?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        return a.style == b.style && a.size == b.size && a.family.equals(b.family, ignoreCase = true)
    }

    private fun chooseFontForElement(element: String, primary: Font, fallback: Font, cache: GlyphRunCache): Font {
        for (font in FontFallback.candidateFonts(primary, fallback)) {
            if (cache.canDisplayTextElement(font, element)) return font
        }
        return primary
    }

    fun splitRuns(text: String, primary: Font, fallback: Font, cache: GlyphRunCache): List<Run> {
        if (text.isEmpty()) return emptyList()
        val runs = ArrayList<Run>()
        val buf = StringBuilder()
        var current: Font? = null

        for (element in textElements(text)) {
            val chosen = chooseFontForElement(element, primary, fallback, cache)
            if (current == null) {
                current = chosen
                buf.append(element)
                continue
            }
            if (sameFamilyStyleSize(current, chosen)) {
                buf.append(element)
            } else {
                runs.add(Run(buf.toString(), current))
                buf.setLength(0)
                buf.append(element)
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

        for (element in textElements(s)) {
            if (element == "\n") {
                out.add(line.toString())
                line.setLength(0)
                continue
            }

            line.append(element)
            if (measureTextRunAware(g, line.toString(), primary, fallback, cache) > maxWidth) {
                line.setLength(line.length - element.length)
                if (line.isNotEmpty()) {
                    out.add(line.toString())
                    line.setLength(0)
                }
                if (!element.isBlank()) line.append(element)
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
        val elements = textElements(text)
        var lo = 0
        var hi = elements.size

        while (lo < hi) {
            val mid = (lo + hi) / 2
            val s = elements.take(mid).joinToString("") + ell
            if (measureTextRunAware(g, s, primary, fallback, cache) <= maxPx) lo = mid + 1 else hi = mid
        }

        val cut = (lo - 1).coerceAtLeast(0)
        return elements.take(cut).joinToString("") + ell
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

    private fun textElements(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var i = 0

        while (i < text.length) {
            val start = i
            var cp = text.codePointAt(i)
            i += Character.charCount(cp)
            i = consumeMarksAndSelectors(text, i)

            if (isRegionalIndicator(cp) && i < text.length) {
                val next = text.codePointAt(i)
                if (isRegionalIndicator(next)) {
                    i += Character.charCount(next)
                    i = consumeMarksAndSelectors(text, i)
                }
            }

            while (i < text.length) {
                val next = text.codePointAt(i)
                if (next == 0x200D) {
                    i += Character.charCount(next)
                    if (i < text.length) {
                        cp = text.codePointAt(i)
                        i += Character.charCount(cp)
                        i = consumeMarksAndSelectors(text, i)
                        continue
                    }
                }
                if (next == 0x20E3) {
                    i += Character.charCount(next)
                    i = consumeMarksAndSelectors(text, i)
                    continue
                }
                break
            }

            out.add(text.substring(start, i))
        }

        return out
    }

    private fun consumeMarksAndSelectors(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isVariationSelector(cp) || isCombiningMark(cp) || isEmojiModifier(cp)) {
                i += Character.charCount(cp)
            } else {
                break
            }
        }
        return i
    }

    private fun isTransparentForFontFallback(codePoint: Int): Boolean {
        return codePoint == 0x200D ||
                isVariationSelector(codePoint) ||
                Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(codePoint) == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun isVariationSelector(codePoint: Int): Boolean {
        return codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isEmojiModifier(codePoint: Int): Boolean {
        return codePoint in 0x1F3FB..0x1F3FF
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean {
        return codePoint in 0x1F1E6..0x1F1FF
    }

}
