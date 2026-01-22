package top.chiloven.lukosbot2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An outgoing message with optional attachments.
 *
 * @param addr        address
 * @param text        message text
 * @param attachments list of attachments
 */
public record MessageOut(Address addr, String text, List<Attachment> attachments) {
    public MessageOut(Address addr, String text) {
        this(addr, text, new ArrayList<>());
    }

    /**
     * Create a simple text message without attachments.
     *
     * @param addr address
     * @param text message text
     * @return a new MessageOut instance with the specified text and no attachments
     */
    public static MessageOut text(Address addr, String text) {
        return new MessageOut(addr, text, new ArrayList<>());
    }

    /**
     * Add an attachment to the message and return a new MessageOut instance.
     *
     * @param att the attachment to add
     * @return a new MessageOut instance with the added attachment
     */
    public MessageOut with(Attachment att) {
        List<Attachment> list = new ArrayList<>(attachments == null ? List.of() : attachments);
        list.add(att);
        return new MessageOut(addr, text, list);
    }
}
