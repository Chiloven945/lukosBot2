package top.chiloven.lukosbot2.util

import top.chiloven.lukosbot2.util.StringUtils.CMD_TO_ANSI_MAP
import top.chiloven.lukosbot2.util.StringUtils.fmtTimeSec
import top.chiloven.lukosbot2.util.StringUtils.formatNum
import top.chiloven.lukosbot2.util.StringUtils.formatStackTrace
import top.chiloven.lukosbot2.util.StringUtils.normalizeLf
import top.chiloven.lukosbot2.util.StringUtils.replaceWithMap
import top.chiloven.lukosbot2.util.StringUtils.truncateText
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.util.*
import java.util.regex.Pattern
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
     * Normalizes all whitespace characters into single spaces, trims leading and
     * trailing whitespace, and truncates the string to a maximum length.
     *
     * If the normalized text length exceeds [limit], it is capped at `limit - 1`
     * characters and appended with an ellipsis (`…`).
     *
     * @param s The raw input string to process.
     * @param limit The maximum allowed length of the returned string. Must be greater than 0.
     * @return The normalized, trimmed, and optionally truncated string. Returns an empty
     * string if [limit] is less than or equal to 0.
     */
    @JvmStatic
    @JvmOverloads
    fun truncateText(s: String, limit: Int = 200): String {
        if (limit <= 0) return ""
        val t = s.replace(Regex("\\s+"), " ").trim()
        return if (t.length > limit) "${t.take(limit - 1)}…" else t
    }

    /**
     * Extension variant of [truncateText] to allow idiomatic Kotlin chaining.
     *
     * Normalizes whitespace, trims, and truncates this string to the specified [limit].
     *
     * @param limit The maximum allowed length of the returned string. Defaults to 200.
     * @return The processed string.
     * @see truncateText
     */
    fun String.truncate(limit: Int = 200): String {
        return truncateText(this, limit)
    }

    /**
     * Provides a null-coalescing fallback mechanism, primarily for Java interoperability.
     *
     * Returns the original string if it is not null, otherwise returns the specified [replace] value.
     * For native Kotlin code, use the Elvis operator `?:` directly instead.
     *
     * @param str The input string that may be null.
     * @param replace The fallback string to use if [str] is null. Defaults to an empty string.
     * @return The original [str] if non-null; otherwise [replace].
     */
    @JvmStatic
    @JvmOverloads
    fun replaceNull(str: String?, replace: String = ""): String = str ?: replace

    /**
     * Finds the first string sequence that is neither null, empty, nor consisting solely of whitespace.
     *
     * Evaluates the [candidates] sequentially from left to right.
     *
     * @param candidates A variable number of optional string arguments to evaluate.
     * @return The first non-blank string found among the [candidates], or an empty string
     * if none match the criteria.
     */
    @JvmStatic
    fun firstNonBlank(vararg candidates: String?): String =
        candidates.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    /**
     * Formats a large number into a human-readable string using localized metric suffixes.
     *
     * Abbreviates values using `k` (thousands), `M` (millions), `B` (billions), and `T` (trillions).
     * Values below 1,000 are returned as a raw string representation without decimal formatting.
     *
     * *Note: This implementation currently only formats positive values correctly. Negative values*
     * *will fall straight through to the `else` branch and return their raw string form.*
     *
     * @param n The number to format.
     * @param pattern The [java.text.DecimalFormat] pattern used for the floating-point division. Defaults to `#0.0`.
     * @return The formatted string appended with the appropriate metric unit suffix.
     */
    @JvmStatic
    @JvmOverloads
    fun formatNum(n: Long, pattern: String = "#0.0"): String {
        val df = DecimalFormat(pattern)
        return when {
            n >= 1_000_000_000_000L -> "${df.format(n / 1_000_000_000_000.0)}T"
            n >= 1_000_000_000L -> "${df.format(n / 1_000_000_000.0)}B"
            n >= 1_000_000L -> "${df.format(n / 1_000_000.0)}M"
            n >= 1_000L -> "${df.format(n / 1_000.0)}k"
            else -> n.toString()
        }
    }

    /**
     * Extension variant of [formatNum] to allow idiomatic Kotlin chaining on [Long] values.
     *
     * Formats this number into a human-readable string with unit suffixes (`k`, `M`, `B`, `T`).
     *
     * @param pattern The [java.text.DecimalFormat] pattern used for the floating-point division. Defaults to `#0.0`.
     * @return The formatted string appended with the appropriate metric unit suffix.
     * @see formatNum
     */
    fun Long.fmtNum(pattern: String = "#0.0"): String {
        return formatNum(this, pattern)
    }

    /**
     * Encodes a string into the application/x-www-form-urlencoded format using the specified charset.
     *
     * @param str The raw string to translate.
     * @param sc The target [Charset] for the encoding process. Defaults to [StandardCharsets.UTF_8].
     * @return The translated, URL-encoded string.
     * @see java.net.URLEncoder.encode
     */
    @JvmStatic
    @JvmOverloads
    fun encodeTo(str: String, sc: Charset = StandardCharsets.UTF_8): String {
        return URLEncoder.encode(str, sc)
    }

    /**
     * Checks whether this string matches a standard URL format structure.
     *
     * Validates strings beginning with `http`, `https`, `ftp`, or `file` protocols
     * followed by standard web-safe character sequences.
     *
     * *Note: This regex matches raw strings and will instantiate a new [Regex] object*
     * *on every single execution. For high-frequency loops, consider caching the regex instance.*
     *
     * @return `true` if the string conforms to the URL pattern; `false` otherwise.
     */
    fun String.isUrl(): Boolean = matches(
        ("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").toRegex()
    )

    /**
     * Appends a formatted line to this [StringBuilder] only if the provided collection is non-null and not empty.
     *
     * The line is generated using the format: `"$label：$prefix$item1$separator$prefix$item2..."`.
     * If the [items] collection is null or empty, this method does nothing and returns without appending a line.
     *
     * @param label The descriptive title prefix for the appended line (e.g., "Tags").
     * @param items The optional list of strings to join and append.
     * @param separator The character sequence used to split items. Defaults to a Chinese enumeration comma (`"、"`).
     * @param prefix an optional string to prepend to every individual element in the [items] list before joining.
     */
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

    /**
     * Appends a header [label] followed by a multi-line, bulleted list of [items] if the collection is non-null and not empty.
     *
     * The section is structured with the label on its own line, and each item indented on a new line:
     * ```text
     * Label：
     *   - Item 1
     *   - Item 2
     * ```
     * If the [items] collection is null or empty, this method terminates instantly without modifying the buffer.
     *
     * @param label The category header title for the bulleted section.
     * @param items The optional list of text lines to output.
     * @param prefix The character indentation or bullet style prepended to every item. Defaults to Markdown bullets (`"  - "`).
     * @param blankLineAfter If `true`, appends an extra trailing newline (`\n`) to pad spacing before subsequent sections.
     */
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

    /**
     * Appends a single line of text if the provided [value] string is neither null, empty, nor exclusively whitespace.
     *
     * The line layout dynamically adapts based on the presence of a [label]:
     * * **Label provided:** Appends exactly as `"$label：$value"`.
     * * **Label null or blank:** Appends the raw [value] string completely standalone.
     *
     * @param value The raw string message to check and append.
     * @param label An optional descriptive prefix. If null, empty, or blank, it is omitted from formatting entirely.
     * @param blankLineAfter If `true`, inserts an empty spacer line below the appended contents.
     */
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

    /**
     * Formats a raw byte count into a human-readable data size string using binary prefix units (IEC standard).
     *
     * Automatically scales values up across binary increments: `B`, `KiB`, `MiB`, `GiB`, `TiB`, `PiB`, and `EiB`.
     * The method accommodates negative byte values by carrying their sign through to the final output string.
     *
     * ### Edge-Case Rounding Correction
     * Includes logical adjustment constraints to prevent misleading display overflows. For example, if a value
     * resolves to `1023.99 MiB` and [decimals] is configured to `1`, regular roundings can result in `1024.0 MiB`.
     * This utility catches that specific boundary condition and correctly promotes the expression to `1.0 GiB`.
     *
     * @param bytes The total quantity of bytes to scale and format. Can be negative.
     * @param decimals The fixed floating-point decimal precision constraint. Must fall within the range `0..6`. Defaults to `1`.
     * @return A localized human-readable representation string (e.g., `"-12.5 MiB"` or `"512 B"`).
     * @throws IllegalArgumentException If [decimals] is specified outside the supported structural safe bounds of `0..6`.
     */
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

    /**
     * Formats a total duration in seconds into an adaptive, human-readable time string (`d:hh:mm:ss`).
     *
     * The output layout dynamically scales down based on the size of the duration:
     * * **>= 24 hours:** `"d:hh:mm:ss"` (e.g., `"1:05:22:04"`)
     * * **1 to 23 hours:** `"h:mm:ss"` (e.g., `"5:22:04"`)
     * * **< 1 hour:** `"mm:ss"` (e.g., `"04:12"`)
     *
     * ### Behavioral Rules
     * * **Padding:** The leading time component is unpadded (unless it drops down to the minutes block,
     * which enforces a minimum two-digit `"mm:ss"` view). All subsequent elements are zero-padded to two digits.
     * * **Negative Values:** Keeps structural track of negative durations by extracting the numerical absolute value
     * and prepending a minus sign (`"-"`) to the finalized string.
     *
     * @param totalSeconds The complete duration amount measured in seconds.
     * @return An adaptively structured digital clock readout string.
     */
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

    /**
     * Overload variant accepting an integer amount of seconds.
     *
     * @param seconds The total duration in seconds.
     * @return The formatted clock style string.
     * @see fmtTimeSec
     */
    fun fmtTimeSec(seconds: Int): String =
        fmtTimeSec(seconds.toLong())

    /**
     * Overload variant accepting floating-point seconds.
     *
     * Truncates fractional milliseconds towards negative infinity using a floor operation.
     *
     * @param seconds The total fractional duration in seconds.
     * @return The formatted clock style string.
     * @see fmtTimeSec
     */
    fun fmtTimeSec(seconds: Float): String =
        fmtTimeSec(floor(seconds.toDouble()).toLong())

    /**
     * Overload variant accepting double-precision floating-point seconds.
     *
     * Truncates fractional milliseconds towards negative infinity using a floor operation.
     *
     * @param seconds The total fractional duration in seconds.
     * @return The formatted clock style string.
     * @see fmtTimeSec
     */
    fun fmtTimeSec(seconds: Double): String =
        fmtTimeSec(floor(seconds).toLong())

    /**
     * Appends a fixed-length horizontal divider line consisting of 20 hyphens followed by a line break.
     *
     * This utility is used for structurally partitioning text blocks or console readouts.
     * The method returns the [StringBuilder] instance to facilitate fluent API chaining.
     *
     * ### Output Format
     * ```text
     * --------------------
     * ```
     *
     * @return This [StringBuilder] instance for method chaining.
     */
    fun StringBuilder.appendSeparator(): StringBuilder {
        return append("--------------------").appendLine()
    }

    /**
     * Performs a single-pass search and replacement across a target string using a key-value mapping configuration.
     *
     * This utility builds a unified regular expression out of the map keys, with each key safely escaped using
     * [Pattern.quote] to handle special characters. It executes the replacement in a single chronological sweep,
     * preventing downstream cascading replacements (where a substituted value gets accidentally replaced again
     * by a subsequent rule).
     *
     * ### Behavioral Rules
     * * **Null & Empty Safeguards:** If [target] is null, or if [replacements] is null or empty, the original
     * [target] reference is returned immediately without processing.
     * * **Unmapped Keys:** If a regular expression match succeeds but the token fails to map to a valid value
     * inside [replacements], the original matching value is left completely unmodified.
     *
     * @param target The raw input string containing tokens to replace. Can be null.
     * @param replacements A lookup dictionary mapping literal target sequences to their desired substitution text.
     * @return The updated string with all matching tokens replaced, or the original [target] if no mutations occurred.
     * @see java.util.regex.Pattern.quote
     */
    @JvmStatic
    fun replaceWithMap(target: String?, replacements: Map<String, String>?): String? {
        if (target == null || replacements.isNullOrEmpty()) {
            return target
        }

        val regex = Regex(
            replacements.keys.joinToString(
                "|", "(", ")"
            ) { Pattern.quote(it) }
        )

        return regex.replace(target) { matchResult ->
            replacements[matchResult.value] ?: matchResult.value
        }
    }

    /**
     * An immutable lookup map pairing custom styling tokens (prefixed with section sign `§`)
     * with their standard terminal ANSI escape character color sequences.
     *
     * Matches typical console rendering schemes:
     * * `§0` to `§7`: Standard low-intensity terminal colors (Black, Blue, Green, Cyan, Red, Magenta, Yellow, White).
     * * `§8` to `§f`: High-intensity / bright terminal colors.
     * * `§r`: Global style reset sequence (`\u001B[0m`).
     */
    val CMD_TO_ANSI_MAP = mapOf(
        "§0" to "\u001B[30m",
        "§1" to "\u001B[34m",
        "§2" to "\u001B[32m",
        "§3" to "\u001B[36m",
        "§4" to "\u001B[31m",
        "§5" to "\u001B[35m",
        "§6" to "\u001B[33m",
        "§7" to "\u001B[37m",
        "§8" to "\u001B[90m",
        "§9" to "\u001B[94m",
        "§a" to "\u001B[92m",
        "§b" to "\u001B[96m",
        "§c" to "\u001B[91m",
        "§d" to "\u001B[95m",
        "§e" to "\u001B[93m",
        "§f" to "\u001B[97m",
        "§r" to "\u001B[0m",
    )

    /**
     * Translates custom formatting codes within a string into actionable ANSI terminal colors.
     *
     * Utilizes a single-pass regex compilation routine driven by [CMD_TO_ANSI_MAP].
     *
     * *Note: The non-null assertion `!!` is guaranteed safe here since [target] is non-null*
     * *and the fallback handler within [replaceWithMap] returns the original text on no-match.*
     *
     * @param target The raw console text string to colorize.
     * @return The updated string containing embedded ANSI escape structures.
     * @see replaceWithMap
     * @see CMD_TO_ANSI_MAP
     */
    @JvmStatic
    fun resolveColorCode(target: String): String =
        replaceWithMap(target, CMD_TO_ANSI_MAP)!!

    /**
     * Extracts an exception's internal stack frame tracking array and converts it into a flattened multi-line string.
     *
     * Every individual element row is assigned to its own line and prefixed explicitly with an indented tab notation (`\tat `).
     *
     * *Note: This implementation only formats the raw stack array data. It completely omits*
     * *the leading exception header message, underlying nested causes, or suppressed trace data.*
     *
     * @param e The exception object containing the trace array to extract.
     * @return A multi-line trace string isolated by newline (`\n`) separators.
     */
    @JvmStatic
    fun formatStackTrace(e: Exception): String {
        return e.stackTrace.joinToString(separator = "\n") { element ->
            "\tat $element"
        }
    }

    /**
     * Extension variant of [formatStackTrace] supporting fluent chaining layouts on [Exception] instances.
     *
     * Converts raw trace elements into indented tab-prefixed string blocks.
     *
     * @return A multi-line trace string layout.
     * @see formatStackTrace
     */
    fun Exception.fmtSt(): String {
        return formatStackTrace(this)
    }

    /**
     * Finds the index of the first whitespace character in the specified string.
     *
     * Scans the string sequentially from left to right using [Char.isWhitespace]
     * to identify standard spaces, tabs, line breaks, or other Unicode whitespace sequences.
     *
     * @param s The input string to inspect.
     * @return The 0-indexed position of the first whitespace character encountered,
     * or `-1` if the string contains no whitespace or is empty.
     * @see Char.isWhitespace
     */
    @JvmStatic
    fun indexOfWhitespace(s: String): Int {
        return s.indexOfFirst { it.isWhitespace() }
    }

    /**
     * Normalizes line breaks in the text by converting Windows CRLF sequences into Unix LF newlines,
     * and trims leading and trailing whitespace from the final result.
     *
     * *Note: This operation affects the entire string, altering internal line endings to `\n`*
     * *while stripping out accidental surrounding spaces or empty lines at the boundaries.*
     *
     * @param s The input string containing text to normalize.
     * @return The cleaned string using consistent line feed (`\n`) line endings without surrounding whitespace.
     */
    @JvmStatic
    fun normalizeLf(s: String): String {
        return s.replace("\r\n", "\n").trim()
    }

    /**
     * Extension variant of [normalizeLf] supporting fluent chaining layouts on [String] instances.
     *
     * Converts internal carriage return line feeds (`\r\n`) to standard line feeds (`\n`) and trims boundaries.
     *
     * @return A normalized and trimmed string layout.
     * @see normalizeLf
     */
    fun String.crlfToLf(): String = normalizeLf(this)

}
