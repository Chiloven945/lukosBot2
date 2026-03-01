package top.chiloven.lukosbot2.model.message.media;

/**
 * Media referenced by a platform-specific file identifier (e.g. Telegram file_id).
 */
public record PlatformFileRef(
        String platform,
        String fileId
) implements MediaRef {

    public PlatformFileRef {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("platform must not be blank");
        }
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId must not be blank");
        }
    }

}
