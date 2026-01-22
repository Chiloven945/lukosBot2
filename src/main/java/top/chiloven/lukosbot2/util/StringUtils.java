package top.chiloven.lukosbot2.util;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for string manipulations.
 *
 * @author Chiloven945
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static StringUtils getStringUtils() {
        return new StringUtils();
    }

    /**
     * Normalize all whitespace to single spaces, trim, and cap to 200 characters with ellipsis.
     *
     * @param s the input string
     * @return the normalized string
     */
    public String truncate(String s) {
        return truncate(s, 200);
    }

    /**
     * Normalize all whitespace to single spaces, trim, and cap to specified limit with ellipsis.
     *
     * @param s     the input string
     * @param limit the maximum length
     * @return the normalized string
     */
    public String truncate(String s, int limit) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        if (limit <= 0) return "";
        return t.length() > limit ? t.substring(0, Math.max(0, limit - 1)) + "…" : t;
    }

    /**
     * Replace null or blank string with a default replacement "-".
     *
     * @param s the string to check
     * @return the original string or "-" if null/blank
     */
    public String replaceNull(String s) {
        return replaceNull(s, "-");
    }

    /**
     * Replace null or blank string with a specified replacement.
     *
     * @param s       the string to check
     * @param replace the replacement string
     * @return the original string or the replacement if null/blank
     */
    public String replaceNull(String s, String replace) {
        return (s == null || s.isBlank()) ? replace : s;
    }

    /**
     * Format a number into a human-readable string with suffixes (k, M, B).
     *
     * @param n       the number to format
     * @param pattern the decimal format pattern
     * @return the formatted string
     */
    public String formatNum(long n, String pattern) {
        DecimalFormat decFor = new DecimalFormat(pattern);
        if (n >= 1_000_000_000L) return decFor.format(n / 1_000_000_000.0) + "B";
        if (n >= 1_000_000L) return decFor.format(n / 1_000_000.0) + "M";
        if (n >= 1_000L) return decFor.format(n / 1_000.0) + "k";
        return String.valueOf(n);
    }

    /**
     * Format a number into a human-readable string with one decimal place(pattern {@code #0.0}) and suffixes (k, M, B).
     *
     * @param n the number to format
     * @return the formatted string
     */
    public String formatNum(long n) {
        return formatNum(n, "#0.0");
    }

    /**
     * Format milliseconds into a date-time string with pattern {@code yyyy-MM-dd HH:mm:ss}.
     *
     * @param millis the milliseconds to format
     * @return the formatted date-time string
     */
    public String formatTime(long millis) {
        return formatTime(millis, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Format milliseconds into a date-time string with the specified pattern.
     *
     * @param millis  the milliseconds to format
     * @param pattern the date-time pattern
     * @return the formatted date-time string
     * @see SimpleDateFormat
     */
    public String formatTime(long millis, String pattern) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat(pattern).format(new Date(millis));
    }

    public String encodeTo(String str, Charset sc) {
        return URLEncoder.encode(str, sc);
    }

    public String encodeTo(String str) {
        return encodeTo(str, StandardCharsets.UTF_8);
    }
}
