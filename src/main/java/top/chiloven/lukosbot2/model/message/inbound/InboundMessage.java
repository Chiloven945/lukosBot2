package top.chiloven.lukosbot2.model.message.inbound;

import top.chiloven.lukosbot2.model.message.Address;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * New inbound message model.
 *
 * <p>This model is designed to carry richer information than the legacy {@code MessageIn},
 * including non-text parts (image/file), metadata, and platform-specific extensions.</p>
 */
public record InboundMessage(
        Address addr,
        Sender sender,
        Chat chat,
        MessageMeta meta,
        List<InPart> parts,
        Map<String, Object> ext
) {

    public InboundMessage {
        if (sender == null) sender = Sender.unknown();
        if (chat == null) chat = new Chat(addr, null);
        if (meta == null) meta = MessageMeta.empty();
        if (parts == null) parts = List.of();
        if (ext == null) ext = Map.of();
    }

    public List<InPart> partsSafe() {
        return parts == null ? Collections.emptyList() : parts;
    }

}
