package top.chiloven.lukosbot2.util

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Utility class for string manipulations.
 * 
 * @author Chiloven945
 */
object StringUtils {
    /**
     * Normalize all whitespace to single spaces, trim, and cap to specified limit with ellipsis.
     * 
     * @param s     the input string
     * @param limit the maximum length
     * @return the normalized string
     */
    @JvmStatic
    @JvmOverloads
    fun truncateText(s: String, limit: Int = 200): String {
        if (limit <= 0) return ""
        val t = s.replace(Regex("\\s+"), " ").trim()
        return if (t.length > limit) "${t.take(limit - 1)}…" else t
    }

    fun String.truncate(limit: Int = 200): String {
        return truncateText(this, limit)
    }

    /**
     * Format a number into a human-readable string with suffixes (k, M, B).
     * 
     * @param n       the number to format
     * @param pattern the decimal format pattern
     * @return the formatted string
     */
    @JvmStatic
    @JvmOverloads
    fun formatNum(n: Long, pattern: String = "#0.0"): String {
        val df = DecimalFormat(pattern)
        return when {
            n >= 1_000_000_000L -> "${df.format(n / 1_000_000_000.0)}B"
            n >= 1_000_000L -> "${df.format(n / 1_000_000.0)}M"
            n >= 1_000L -> "${df.format(n / 1_000.0)}k"
            else -> n.toString()
        }
    }

    fun Long.fmtNum(pattern: String = "#0.0"): String {
        return formatNum(this, pattern)
    }

    /**
     * Format milliseconds into a date-time string with the specified pattern.
     * 
     * @param millis  the milliseconds to format
     * @param pattern the date-time pattern
     * @return the formatted date-time string
     * @see SimpleDateFormat
     */
    @JvmStatic
    @JvmOverloads
    fun formatTime(millis: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String? {
        if (millis <= 0) return "-"
        return SimpleDateFormat(pattern).format(Date(millis))
    }

    fun Long.fmtTime(pattern: String = "yyyy-MM-dd HH:mm:ss"): String? {
        return formatTime(this, pattern)
    }

    @JvmStatic
    @JvmOverloads
    fun encodeTo(str: String, sc: Charset = StandardCharsets.UTF_8): String? {
        return URLEncoder.encode(str, sc)
    }

    fun String.isUrl(): Boolean = matches(
        ("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").toRegex()
    )

    fun Boolean.toText(): String = if (this) "是" else "否"

    fun StringBuilder.appendLineIfNotEmpty(
        label: String,
        items: List<String>?,
        separator: String = "、",
        prefix: String = ""
    ) {
        items
            ?.takeIf { it.isNotEmpty() }
            ?.let { list ->
                appendLine("$label：${list.joinToString(separator) { prefix + it }}")
            }
    }

    fun StringBuilder.appendSectionIfNotEmpty(
        label: String,
        items: List<String>?,
        prefix: String = "  - ",
        blankLineAfter: Boolean = false
    ) {
        val list = items?.takeIf { it.isNotEmpty() } ?: return
        append(label).append("：\n")
        list.forEach { append(prefix).append(it).append('\n') }
        if (blankLineAfter) append('\n')
    }

    fun StringBuilder.appendLineIfNotEmpty(
        value: String?,
        label: String? = null,
        blankLineAfter: Boolean = false
    ) {
        value
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (label.isNullOrBlank()) appendLine(it)
                else appendLine("$label：$it")
                if (blankLineAfter) appendLine()
            }
    }

    fun fmtBytes(bytes: Long, decimals: Int = 1): String {
        require(decimals in 0..6) { "decimals must be between 0 and 6" }

        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
        val sign = if (bytes < 0) "-" else ""

        val absBytes = abs(bytes.toDouble())
        if (absBytes < 1024.0) return "$bytes B"

        val base = 1024.0
        val exp = (ln(absBytes) / ln(base)).toInt().coerceIn(0, units.lastIndex)
        val value = absBytes / base.pow(exp.toDouble())

        val rounded = String.format(Locale.ROOT, "%.${decimals}f", value).toDouble()
        return if (rounded >= 1024.0 && exp < units.lastIndex) {
            val value2 = absBytes / base.pow((exp + 1).toDouble())
            "$sign${String.format(Locale.ROOT, "%.${decimals}f", value2)} ${units[exp + 1]}"
        } else {
            "$sign${String.format(Locale.ROOT, "%.${decimals}f", value)} ${units[exp]}"
        }
    }

    fun fmtTimeSec(totalSeconds: Long): String {
        val sign = if (totalSeconds < 0) "-" else ""
        var s = abs(totalSeconds)

        val days = s / 86_400
        s %= 86_400
        val hours = s / 3_600
        s %= 3_600
        val minutes = s / 60
        val seconds = s % 60

        val start = when {
            days > 0L -> 0
            hours > 0L -> 1
            else -> 2
        }

        val parts = listOf(days, hours, minutes, seconds)

        fun firstPart(i: Int, v: Long): String =
            if (i == 2) v.toString().padStart(2, '0') else v.toString()

        fun laterPart(v: Long): String = v.toString().padStart(2, '0')

        val out = buildString {
            for (i in start..3) {
                if (i > start) append(':')
                val v = parts[i]
                append(if (i == start) firstPart(i, v) else laterPart(v))
            }
        }

        return sign + out
    }

    fun fmtTimeSec(seconds: Int): String =
        fmtTimeSec(seconds.toLong())

    fun fmtTimeSec(seconds: Float): String =
        fmtTimeSec(floor(seconds.toDouble()).toLong())

    fun fmtTimeSec(seconds: Double): String =
        fmtTimeSec(floor(seconds).toLong())

}
