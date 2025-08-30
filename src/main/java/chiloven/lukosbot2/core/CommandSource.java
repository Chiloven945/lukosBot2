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
     * 在命令中调用：把文本回复追加到当前消息的输出列表
     */
    public void reply(String text) {
        sink.accept(MessageOut.replyTo(in, text));
    }
}
