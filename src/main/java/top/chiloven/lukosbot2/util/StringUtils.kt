package top.chiloven.lukosbot2.util

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

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
    fun truncate(s: String?, limit: Int = 200): String {
        if (s == null) return ""
        val t = s.replace("\\s+".toRegex(), " ").trim { it <= ' ' }
        if (limit <= 0) return ""
        return if (t.length > limit) t.substring(0, max(0, limit - 1)) + "…" else t
    }

    /**
     * Replace null or blank string with a specified replacement.
     * 
     * @param s       the string to check
     * @param replace the replacement string
     * @return the original string or the replacement if null/blank
     */
    @JvmStatic
    @JvmOverloads
    fun replaceNull(s: String?, replace: String? = "-"): String? {
        return if (s == null || s.isBlank()) replace else s
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
        val decFor = DecimalFormat(pattern)
        if (n >= 1000000000L) return decFor.format(n / 1000000000.0) + "B"
        if (n >= 1000000L) return decFor.format(n / 1000000.0) + "M"
        if (n >= 1000L) return decFor.format(n / 1000.0) + "k"
        return n.toString()
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

    @JvmStatic
    @JvmOverloads
    fun encodeTo(str: String, sc: Charset = StandardCharsets.UTF_8): String? {
        return URLEncoder.encode(str, sc)
    }
}
