package chiloven.lukosbot2.model;

public record MessageOut(Address addr, String text) {

    /**
     * 工具方法：从入站消息直接回复
     */
    public static MessageOut replyTo(MessageIn in, String text) {
        return new MessageOut(in.addr(), text);
    }
}
