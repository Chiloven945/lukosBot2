package top.chiloven.lukosbot2.core;

import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;

import java.util.List;

/**
 * A message processor that consumes an inbound message and produces zero or more outbound messages.
 *
 * <p>Processors are typically chained by {@link PipelineProcessor}.</p>
 */
public interface IProcessor {

    /**
     * Handle an inbound message.
     *
     * @param in inbound message
     * @return outbound messages to be sent (may be empty, never null)
     */
    List<OutboundMessage> handle(InboundMessage in);

}
