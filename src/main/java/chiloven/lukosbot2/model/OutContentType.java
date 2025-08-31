package chiloven.lukosbot2.model;

/**
 * Outgoing content type
 */
public enum OutContentType {
    TEXT,       // 兼容：MessageOut.text
    IMAGE,      // 图片（url 或 bytes）
    FILE        // 一般文件（url 或 bytes）
}
