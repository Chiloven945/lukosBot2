package top.chiloven.lukosbot2.util;

import java.time.format.DateTimeFormatter;

public class TimeUtils {
    private TimeUtils() {
    }

    public static DateTimeFormatter getDTF() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
}
