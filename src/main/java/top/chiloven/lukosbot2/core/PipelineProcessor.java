package top.chiloven.lukosbot2.core;

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple sequential processor pipeline.
 *
 * <p>Each processor may contribute zero or more outbound messages. The pipeline concatenates
 * results in processor order.</p>
 */
@Service
public class PipelineProcessor {

    private final List<IProcessor> processors;

    public PipelineProcessor(List<IProcessor> processors) {
        this.processors = processors == null ? Collections.emptyList() : processors;
    }

    public List<OutboundMessage> handle(InboundMessage in) {
        if (processors.isEmpty()) return List.of();

        List<OutboundMessage> outs = new ArrayList<>();
        for (IProcessor p : processors) {
            if (p == null) continue;
            List<OutboundMessage> o = p.handle(in);
            if (o != null && !o.isEmpty()) outs.addAll(o);
        }
        return outs;
    }

}
