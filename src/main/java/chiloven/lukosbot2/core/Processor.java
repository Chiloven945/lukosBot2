package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.List;

public interface Processor {
    List<MessageOut> handle(MessageIn in);
}
