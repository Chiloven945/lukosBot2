package top.chiloven.lukosbot2.model.message.outbound;

/**
 * Plain text to send.
 */
public record OutText(String text) implements OutPart {

    @Override
    public OutPartType type() {
        return OutPartType.TEXT;
    }

}
