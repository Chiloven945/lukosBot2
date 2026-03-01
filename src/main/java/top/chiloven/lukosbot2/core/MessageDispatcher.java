package top.chiloven.lukosbot2.core;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.service.ServiceManager;
import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.util.concurrent.Execs;
import top.chiloven.lukosbot2.util.message.MessageIoLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Entry point for inbound messages.
 *
 * <p>Unlike the previous per-chat single-lane dispatcher, this dispatcher processes messages concurrently so multiple
 * commands can run at the same time (even in the same chat). Outbound sending is still serialized per chat by
 * {@link MessageSenderHub}.</p>
 */
@Service
@Log4j2
public class MessageDispatcher {

    private final MessageSenderHub senderHub;
    private final PipelineProcessor pipeline;
    private final ServiceManager services;

    /**
     * Concurrent executor for message processing.
     */
    private final ExecutorService exec = Execs.newVirtualExecutor("proc-");

    public MessageDispatcher(MessageSenderHub senderHub, PipelineProcessor pipeline, ServiceManager services) {
        this.senderHub = senderHub;
        this.pipeline = pipeline;
        this.services = services;
    }

    public void receive(InboundMessage in) {
        if (in == null || in.addr() == null) return;

        MessageIoLog.inbound(in);

        exec.submit(() -> {
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

}
