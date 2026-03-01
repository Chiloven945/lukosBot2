package top.chiloven.lukosbot2.model.message.inbound;

/**
 * Metadata for an inbound message.
 *
 * <p>All fields are optional because different platforms expose different metadata.</p>
 */
public record MessageMeta(
        String messageId,
        Long timestampMs,
        String replyToMessageId,
        String rawType
) {

    public static MessageMeta empty() {
        return new MessageMeta(
                null,
                null,
                null,
                null
        );
    }

}
