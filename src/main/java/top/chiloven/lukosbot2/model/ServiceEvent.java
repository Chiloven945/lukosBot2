package top.chiloven.lukosbot2.model;

import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;

public final class ServiceEvent {

    private final Kind kind;
    private final InboundMessage message;
    private final String key;
    private final Object payload;

    private ServiceEvent(Kind kind, InboundMessage message, String key, Object payload) {
        this.kind = kind;
        this.message = message;
        this.key = key;
        this.payload = payload;
    }

    public static ServiceEvent message(InboundMessage in) {
        return new ServiceEvent(Kind.MESSAGE, in, null, null);
    }

    public static ServiceEvent external(String key, Object payload) {
        return new ServiceEvent(Kind.EXTERNAL, null, key, payload);
    }

    public Kind kind() {
        return kind;
    }

    public InboundMessage message() {
        return message;
    }

    public String key() {
        return key;
    }

    public Object payload() {
        return payload;
    }

    public enum Kind {
        MESSAGE,
        EXTERNAL
    }

}
