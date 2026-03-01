package top.chiloven.lukosbot2.model.message.outbound;

import top.chiloven.lukosbot2.model.message.media.MediaRef;

/**
 * Image to send.
 */
public record OutImage(
        MediaRef ref,
        String caption,
        String name,
        String mime
) implements OutPart {

    public OutImage {
        if (caption != null && caption.isBlank()) caption = null;
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
    }

    @Override
    public OutPartType type() {
        return OutPartType.IMAGE;
    }

}
