package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class Router {
    private static final Logger log = LogManager.getLogger(Router.class);

    private final Processor pipeline;
    private final Sender sender;

    public Router(Processor pipeline, Sender sender) {
        this.pipeline = pipeline;
        this.sender = sender;
    }

    /**
     * 接收消息并处理
     *
     * @param in 接收到的消息
     */
    public void receive(MessageIn in) {
        List<MessageOut> outs = pipeline.handle(in);
        if (outs == null || outs.isEmpty()) return;
        for (MessageOut out : outs) sender.send(out);
    }

}
