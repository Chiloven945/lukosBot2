package top.chiloven.lukosbot2.model;

/**
 * A simple record to hold content data such as filename, MIME type, and byte array.
 *
 * @param filename the name of the file
 * @param mime     the MIME type of the file
 * @param bytes    the file content as a byte array
 */
public record ContentData(String filename, String mime, byte[] bytes) {
}
