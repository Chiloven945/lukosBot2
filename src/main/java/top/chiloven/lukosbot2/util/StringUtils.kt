package top.chiloven.lukosbot2.util

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

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
    fun truncate(s: String, limit: Int = 200): String {
        if (limit <= 0) return ""
        val t = s.replace(Regex("\\s+"), " ").trim()
        return if (t.length > limit) "${t.take(limit - 1)}…" else t
    }

    fun String.truncate(limit: Int = 200): String {
        return truncate(this, limit)
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

    fun Long.formatNum(pattern: String = "#0.0"): String {
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

    fun Long.formatTime(pattern: String = "yyyy-MM-dd HH:mm:ss"): String? {
        return formatTime(this, pattern)
    }

    @JvmStatic
    @JvmOverloads
    fun encodeTo(str: String, sc: Charset = StandardCharsets.UTF_8): String? {
        return URLEncoder.encode(str, sc)
    }
}
