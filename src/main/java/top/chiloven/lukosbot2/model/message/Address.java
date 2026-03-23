package top.chiloven.lukosbot2.model.message;

import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.platform.ChatPlatform;

/**
 * Address of a chat (user or group)
 *
 * @param platform the chat platform
 * @param chatId   the chat ID
 * @param group    whether the address is a group chat
 */
public record Address(
        ChatPlatform platform,
        long chatId,
        boolean group
) {

    /**
     * Parse string to {@link Address} in {@code <platform>:<p|g>:<id>} format.
     *
     * @param str the string to be parsed
     * @return the Address result
     */
    public static Address parse(@NonNull String str) {
        String raw = str.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Address cannot be blank.");
        }

        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid address format: " + raw + ". Expected <platform>:<p|g>:<id>."
            );
        }

        ChatPlatform platform;
        try {
            platform = ChatPlatform.fromString(parts[0].trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown platform: " + parts[0], e);
        }

        String type = parts[1].trim().toLowerCase();
        boolean group = switch (type) {
            case "g" -> true;
            case "p" -> false;
            default -> throw new IllegalArgumentException(
                    "Invalid chat type: " + parts[1] + ". Expected 'p' or 'g'."
            );
        };

        long chatId;
        try {
            chatId = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid chat id: " + parts[2], e);
        }

        return new Address(platform, chatId, group);
    }

    @Override
    public @NonNull String toString() {
        return platform.name() + ":" + (group ? "g" : "p") + ":" + chatId;
    }

}
