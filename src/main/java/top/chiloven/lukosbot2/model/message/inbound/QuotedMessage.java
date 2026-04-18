package top.chiloven.lukosbot2.model.message.inbound;

import java.util.Collections;
import java.util.List;

/**
 * Lightweight snapshot of a quoted / replied-to inbound message.
 */
public record QuotedMessage(
        String messageId,
        Long senderId,
        List<InPart> parts
) {

    public QuotedMessage {
        if (parts == null) parts = List.of();
    }

    public List<InPart> partsSafe() {
        return parts == null ? Collections.emptyList() : parts;
    }

}
