package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.spi.Sender;
import chiloven.lukosbot2.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * A Sender implementation that routes messages to different Sender instances based on the ChatPlatform.
 * This allows for a unified interface to send messages across multiple platforms.
 */
public class MessageSenderHub implements Sender {
    private static final Logger log = LogManager.getLogger(MessageSenderHub.class);
    private final Map<ChatPlatform, Sender> routes = new EnumMap<>(ChatPlatform.class);

    /**
     * Register a Sender for a specific ChatPlatform
     *
     * @param p the chat platform
     * @param s the sender implementation
     */
    public void register(ChatPlatform p, Sender s) {
        routes.put(p, s);
    }

    /**
     * Send a message using the appropriate Sender based on the message's platform
     *
     * @param out message to send
     */
    @Override
    public void send(MessageOut out) {
        int att = (out.attachments() == null) ? 0 : out.attachments().size();
        log.info("OUT -> [{}] to chat={} text=\"{}\" attachments={}",
                out.addr().platform(), out.addr().chatId(), StringUtils.oneLine(out.text()), att);
        Sender s = routes.get(out.addr().platform());
        if (s == null) {
            log.warn("Unable to find the Sender：{}", out.addr().platform());
            return;
        }
        s.send(out);
    }
}
