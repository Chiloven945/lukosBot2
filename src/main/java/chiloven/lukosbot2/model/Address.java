package chiloven.lukosbot2.model;

import chiloven.lukosbot2.platform.ChatPlatform;

/**
 * Address of a chat (user or group)
 *
 * @param platform the chat platform
 * @param chatId  the chat ID
 * @param group  whether the address is a group chat
 */
public record Address(ChatPlatform platform, long chatId, boolean group) {
}
