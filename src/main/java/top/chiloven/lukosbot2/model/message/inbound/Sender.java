package top.chiloven.lukosbot2.model.message.inbound;

/**
 * Sender of an inbound message.
 *
 * <p>Most platforms provide a numeric user id; we keep it as {@link Long} and allow null
 * for system messages or unknown senders.</p>
 */
public record Sender(
        Long id,
        String username,
        String displayName,
        boolean bot
) {

    public Sender {
        // Normalize blanks to null for easier downstream checks
        if (username != null && username.isBlank()) username = null;
        if (displayName != null && displayName.isBlank()) displayName = null;
    }

    public static Sender unknown() {
        return new Sender(
                null,
                null,
                null,
                false
        );
    }

}
