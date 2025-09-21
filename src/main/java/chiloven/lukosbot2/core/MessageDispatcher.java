package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import chiloven.lukosbot2.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class MessageDispatcher {
    private static final Logger log = LogManager.getLogger(MessageDispatcher.class);

    private final Processor pipeline;
    private final Sender sender;

    public MessageDispatcher(Processor pipeline, Sender sender) {
        this.pipeline = pipeline;
        this.sender = sender;
    }

    /**
     * Receive a message, process it through the pipeline, and send out any resulting messages.
     *
     * @param in incoming message
     */
    public void receive(MessageIn in) {
        log.info("IN <- [{}] user={} chat={} text=\"{}\"",
                in.addr().platform(), in.userId(), in.addr().chatId(), StringUtils.oneLine(in.text()));
        List<MessageOut> outs = pipeline.handle(in);
        if (outs == null || outs.isEmpty()) return;
        for (MessageOut out : outs) sender.send(out);
    }

}
