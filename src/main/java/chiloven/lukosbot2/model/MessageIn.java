package chiloven.lukosbot2.model;

/**
 * Incoming message
 *
 * @param addr address
 * @param userId user ID (if available)
 * @param text message text
 */
public record MessageIn(Address addr, Long userId, String text) {
}
