package top.chiloven.lukosbot2.model.message.inbound;

import top.chiloven.lukosbot2.model.message.media.MediaRef;

/**
 * File segment.
 *
 * @param ref     media reference (url, bytes, or platform file id)
 * @param name    optional file name
 * @param mime    optional mime type
 * @param size    optional file size in bytes
 * @param caption optional caption
 */
public record InFile(
        MediaRef ref,
        String name,
        String mime,
        Long size,
        String caption
) implements InPart {

    public InFile {
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
        if (caption != null && caption.isBlank()) caption = null;
    }

    @Override
    public InPartType type() {
        return InPartType.FILE;
    }

}
