package top.chiloven.lukosbot2.core;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.service.ServiceManager;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.util.concurrent.StripedExecutor;
import top.chiloven.lukosbot2.util.message.MessageIoLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for inbound messages.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Provide <b>per-chat</b> serialization for message handling to avoid race conditions.</li>
 *   <li>Invoke services and command pipeline.</li>
 *   <li>Send produced outbound messages via {@link MessageSenderHub}.</li>
 * </ul>
 */
@Service
@Log4j2
public class MessageDispatcher {

    private final MessageSenderHub senderHub;
    private final PipelineProcessor pipeline;
    private final ServiceManager services;
    StripedExecutor lanes = new StripedExecutor(32, "lane-%02d");

    public MessageDispatcher(MessageSenderHub senderHub, PipelineProcessor pipeline, ServiceManager services) {
        this.senderHub = senderHub;
        this.pipeline = pipeline;
        this.services = services;
    }

    public void receive(InboundMessage in) {
        if (in == null || in.addr() == null) return;

        lanes.submit(chatKey(in.addr()), () -> {
            MessageIoLog.inbound(in);
            try {
                List<OutboundMessage> outs = new ArrayList<>();

                // 1) services (should see all messages)
                try {
                    List<OutboundMessage> s = services.onMessage(in);
                    if (s != null && !s.isEmpty()) outs.addAll(s);
                } catch (Exception e) {
                    log.warn("Service processing error: {}", e.getMessage(), e);
                }

                // 2) command pipeline
                try {
                    List<OutboundMessage> p = pipeline.handle(in);
                    if (p != null && !p.isEmpty()) outs.addAll(p);
                } catch (Exception e) {
                    log.warn("Pipeline processing error: {}", e.getMessage(), e);
                }

                // 3) send
                senderHub.sendBatch(outs);
            } catch (Exception e) {
                log.warn("Unexpected dispatcher error: {}", e.getMessage(), e);
            }
        });
    }

    private static String chatKey(Address addr) {
        if (addr == null) return "unknown";
        return addr.platform().name() + ":" + (addr.group() ? "g" : "p") + ":" + addr.chatId();
    }

}
