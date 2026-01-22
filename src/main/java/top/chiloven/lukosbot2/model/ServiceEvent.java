package top.chiloven.lukosbot2.model;

public final class ServiceEvent {

    private final Kind kind;
    private final MessageIn message;
    private final String key;
    private final Object payload;

    private ServiceEvent(Kind kind, MessageIn message, String key, Object payload) {
        this.kind = kind;
        this.message = message;
        this.key = key;
        this.payload = payload;
    }

    public static ServiceEvent message(MessageIn in) {
        return new ServiceEvent(Kind.MESSAGE, in, null, null);
    }

    public static ServiceEvent external(String key, Object payload) {
        return new ServiceEvent(Kind.EXTERNAL, null, key, payload);
    }

    public Kind kind() {
        return kind;
    }

    public MessageIn message() {
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
