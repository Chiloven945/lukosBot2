package chiloven.lukosbot2.model;

/**
 * Attachment for outgoing messages
 *
 * @param type  type of attachment
 * @param name  file name
 * @param url   remote URL
 * @param bytes in-memory data
 * @param mime  optional mime type, e.g. "image/png", "application/pdf"
 */
public record Attachment(OutContentType type, String name, String url, byte[] bytes, String mime) {
    /**
     * Create an image attachment from a URL
     *
     * @param url the image URL
     * @return the attachment
     */
    public static Attachment imageUrl(String url) {
        return new Attachment(OutContentType.IMAGE, null, url, null, null);
    }

    /**
     * Create an image attachment from in-memory bytes
     *
     * @param name  file name
     * @param bytes image data
     * @param mime  optional mime type, e.g. "image/png"
     * @return the attachment
     */
    public static Attachment imageBytes(String name, byte[] bytes, String mime) {
        return new Attachment(OutContentType.IMAGE, name, null, bytes, mime);
    }

    /**
     * Create a file attachment from a URL
     *
     * @param name file name
     * @param url  the file URL
     * @return the attachment
     */
    public static Attachment fileUrl(String name, String url) {
        return new Attachment(OutContentType.FILE, name, url, null, null);
    }

    /**
     * Create a file attachment from in-memory bytes
     *
     * @param name  file name
     * @param bytes file data
     * @param mime  optional mime type, e.g. "application/pdf"
     * @return the attachment
     */
    public static Attachment fileBytes(String name, byte[] bytes, String mime) {
        return new Attachment(OutContentType.FILE, name, null, bytes, mime);
    }
}
