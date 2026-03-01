package top.chiloven.lukosbot2.model.message.inbound;

/**
 * Plain text segment.
 */
public record InText(String text) implements InPart {

    @Override
    public InPartType type() {
        return InPartType.TEXT;
    }

}
