package top.chiloven.lukosbot2.model.message.inbound;

import top.chiloven.lukosbot2.model.message.media.MediaRef;

/**
 * Image segment.
 *
 * @param ref     media reference (url, bytes, or platform file id)
 * @param caption optional caption
 * @param name    optional file name
 * @param mime    optional mime type
 */
public record InImage(
        MediaRef ref,
        String caption,
        String name,
        String mime
) implements InPart {

    public InImage {
        if (caption != null && caption.isBlank()) caption = null;
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
    }

    @Override
    public InPartType type() {
        return InPartType.IMAGE;
    }

}
