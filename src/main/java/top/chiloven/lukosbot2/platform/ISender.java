package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;

/**
 * Platform-specific sender for outbound rich messages.
 *
 * <p>Implementations are responsible for translating {@link OutboundMessage} into
 * native platform API calls (Telegram, Discord, OneBot, ...), including handling of mixed content (text + image/file)
 * and any platform limits.</p>
 */
public interface ISender {

    /**
     * Send an outbound message.
     *
     * <p>This method should be thread-safe. Ordering guarantees (per chat) are handled
     * by {@code MessageSenderHub}.</p>
     *
     * @param out outbound message (must not be null)
     */
    void send(OutboundMessage out);

}
