package chiloven.lukosbot2.model;

/**
 * @param userId 可为空
 * @param text   原始纯文本（适配器负责提取/去除平台特定标记）
 */
public record MessageIn(Address addr, Long userId, String text) {
}
