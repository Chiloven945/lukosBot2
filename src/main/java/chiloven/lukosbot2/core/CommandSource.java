package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.function.Consumer;

public class CommandSource {
    private final MessageIn in;
    private final Consumer<MessageOut> sink;

    public CommandSource(MessageIn in, Consumer<MessageOut> sink) {
        this.in = in;
        this.sink = sink;
    }

    public MessageIn in() {
        return in;
    }

    /**
     * Call this in command: append a text reply to the output list of the current message
     */
    public void reply(String text) {
        sink.accept(MessageOut.replyTo(in, text));
    }
}
