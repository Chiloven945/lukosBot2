package chiloven.lukosbot2.util;

public class StringUtils {
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
}
