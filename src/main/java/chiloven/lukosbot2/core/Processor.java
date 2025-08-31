package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.List;

public interface Processor {
    /**
     * Process an incoming message and return a list of outgoing messages.
     * If no response is needed, return an empty list.
     *
     * @param in the incoming message
     * @return a list of outgoing messages
     */
    List<MessageOut> handle(MessageIn in);
}
