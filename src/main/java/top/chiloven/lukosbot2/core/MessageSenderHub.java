package top.chiloven.lukosbot2.core;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.ISender;
import top.chiloven.lukosbot2.util.concurrent.StripedExecutor;
import top.chiloven.lukosbot2.util.message.MessageIoLog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central hub for routing {@link OutboundMessage} to the correct platform sender.
 *
 * <p>This hub also provides <b>per-chat ordering</b> guarantees: all outbound messages targeting
 * the same chat will be executed sequentially in submission order (striped by chat key).</p>
 */
@Service
@Log4j2
public class MessageSenderHub {

    private final Map<ChatPlatform, ISender> senders = new ConcurrentHashMap<>();
    StripedExecutor lanes = new StripedExecutor(32, "lane-%02d");

    public void register(ChatPlatform platform, ISender sender) {
        if (platform == null || sender == null) return;
        senders.put(platform, sender);
        log.info("Registered sender for platform {}", platform);
    }

    public void sendBatch(List<OutboundMessage> outs) {
        if (outs == null || outs.isEmpty()) return;
        for (OutboundMessage o : outs) {
            send(o);
        }
    }

    public void send(OutboundMessage out) {
        if (out == null) return;
        ChatPlatform platform = out.addr().platform();

        ISender sender = senders.get(platform);
        if (sender == null) {
            log.warn("No sender registered for platform {}, dropping outbound message.", platform);
            return;
        }

        String key = chatKey(out.addr());
        lanes.submit(key, () -> {
            MessageIoLog.outbound(out);
            try {
                sender.send(out);
            } catch (Exception e) {
                log.warn("Failed to send outbound message to {}: {}", key, e.getMessage(), e);
            }
        });
    }

    private static String chatKey(Address addr) {
        if (addr == null) return "unknown";
        return addr.platform().name() + ":" + (addr.group() ? "g" : "p") + ":" + addr.chatId();
    }

    public void shutdown() {
        lanes.shutdown();
    }

}
