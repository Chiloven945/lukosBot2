package top.chiloven.lukosbot2.model.message.media;

/**
 * Media referenced by a remote URL.
 */
public record UrlRef(String url) implements MediaRef {

    public UrlRef {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }

}
