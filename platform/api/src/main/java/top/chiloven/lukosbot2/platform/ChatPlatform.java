package top.chiloven.lukosbot2.platform;

import java.util.Arrays;

/**
 * Supported chat platforms
 */
public enum ChatPlatform {

    TELEGRAM,
    ONEBOT,
    DISCORD;

    public static ChatPlatform fromString(String platform) throws IllegalArgumentException {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ChatPlatform type: " + platform));
    }

}
