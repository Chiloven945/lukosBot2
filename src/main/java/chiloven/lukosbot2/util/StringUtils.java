package chiloven.lukosbot2.util;

import java.text.DecimalFormat;

/**
 * Utility class for string manipulations.
 *
 * @author Chiloven945
 */
public final class StringUtils {

    /**
     * Replace all the wrap to a space, making a string to be written in one line.
     *
     * @param s string to be rewritten
     * @return the rewritten string
     */
    public static String oneLine(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    /**
     * Truncate a string to a certain length, replacing newlines with spaces.
     *
     * @param s     the string to truncate
     * @param limit the maximum length
     * @return the truncated string
     */
    public static String truncate(String s, int limit) {
        String oneLine = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= limit) return oneLine;
        return oneLine.substring(0, limit - 1) + "…";
    }

    /**
     * If the string is null or blank, return "-", otherwise return the string itself.
     *
     * @param s the string to check
     * @return the original string or "-"
     */
    public static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /**
     * Format a number into a human-readable string with suffixes (k, M, B).
     *
     * @param n       the number to format
     * @param pattern the decimal format pattern
     * @return the formatted string
     */
    public static String formatNum(long n, String pattern) {
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
    public static String formatNum(long n) {
        return formatNum(n, "#0.0");
    }
}
