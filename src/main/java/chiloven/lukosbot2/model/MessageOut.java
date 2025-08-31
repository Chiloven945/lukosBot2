package chiloven.lukosbot2.model;

public record MessageOut(Address addr, String text) {

    /**
     * Create a reply message to an incoming message
     *
     * @param in the incoming message
     * @param text the reply text
     * @return the reply message
     */
    public static MessageOut replyTo(MessageIn in, String text) {
        return new MessageOut(in.addr(), text);
    }
}
