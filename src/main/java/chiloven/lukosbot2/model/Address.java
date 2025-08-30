package chiloven.lukosbot2.model;

/**
 * @param chatId 群或私聊唯一 ID（Telegram chatId / OneBot group_id or user_id）
 */
public record Address(ChatPlatform platform, long chatId, boolean group) {
}
