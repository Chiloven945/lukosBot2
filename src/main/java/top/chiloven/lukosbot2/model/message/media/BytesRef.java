package top.chiloven.lukosbot2.model.message.media;

import java.util.Arrays;

/**
 * Media held in memory.
 */
public record BytesRef(
        String name,
        byte[] bytes,
        String mime
) implements MediaRef {

    public BytesRef(byte[] bytes) {
        this(null, bytes, null);
    }

    public BytesRef {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        // defensive copy
        bytes = Arrays.copyOf(bytes, bytes.length);
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

}
