package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.platform.ChatPlatform;
import chiloven.lukosbot2.platform.Sender;
import chiloven.lukosbot2.util.StringUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Central hub for routing and delivering {@link chiloven.lukosbot2.model.MessageOut} messages to platform-specific
 * {@link chiloven.lukosbot2.platform.Sender}s, supporting synchronous sends, asynchronous sends backed by a
 * virtual-thread executor, and batch delivery with optional order preservation; acts as the single egress point
 * so callers don’t manage sender lookups or concurrency themselves.
 */
@Service
@Log4j2
public class MessageSenderHub {
    public static final StringUtils su = StringUtils.getStringUtils();
    private final Map<ChatPlatform, Sender> routes = new EnumMap<>(ChatPlatform.class);

    private final ExecutorService sendPool = Execs.newVirtualExecutor("send-");

    /**
     * Registers (or replaces) the {@link chiloven.lukosbot2.platform.Sender} responsible for delivering messages
     * on the given {@link chiloven.lukosbot2.platform.ChatPlatform}, enabling the hub to route outgoing messages
     * by platform without callers holding sender references.
     *
     * @param p the chat platform whose messages should be handled by the provided sender; must not be {@code null}
     * @param s the sender implementation that performs the actual delivery for the platform; must not be {@code null}
     */
    public void register(ChatPlatform p, Sender s) {
        routes.put(Objects.requireNonNull(p), Objects.requireNonNull(s));
    }

    /**
     * Sends a single {@link chiloven.lukosbot2.model.MessageOut} synchronously by routing it to the registered
     * {@link chiloven.lukosbot2.platform.Sender} for its platform, logging basic metadata and swallowing
     * delivery exceptions to prevent failures from propagating to the caller.
     *
     * @param out the outgoing message to deliver; must not be {@code null} and must contain a valid address with
     *            platform and chat ID
     */
    public void send(MessageOut out) {
        int att = (out.attachments() == null) ? 0 : out.attachments().size();
        log.info("OUT -> [{}] to chat={} text=\"{}\" attachments={}",
                out.addr().platform(), out.addr().chatId(), su.truncate(out.text()), att);
        Sender s = routes.get(out.addr().platform());
        if (s == null) {
            log.warn("No Sender for platform: {}", out.addr().platform());
            return;
        }
        try {
            s.send(out);
        } catch (Throwable ex) {
            log.error("Send failed on platform {}", out.addr().platform(), ex);
        }
    }

    /**
     * Sends a single {@link chiloven.lukosbot2.model.MessageOut} asynchronously by delegating to
     * {@link #send(chiloven.lukosbot2.model.MessageOut)} on a virtual-thread executor, returning a
     * {@link java.util.concurrent.CompletableFuture} that completes when the send finishes or
     * completes exceptionally if delivery fails.
     *
     * @param out the outgoing message to deliver asynchronously; must not be {@code null}
     * @return a future representing the completion of the send operation
     */
    public CompletableFuture<Void> sendAsync(MessageOut out) {
        return CompletableFuture.runAsync(() -> send(out), sendPool);
    }

    /**
     * Sends a list of {@link chiloven.lukosbot2.model.MessageOut} messages either sequentially in the provided
     * order (when {@code preserveOrder} is true) or concurrently using
     * {@link #sendAsync(chiloven.lukosbot2.model.MessageOut)} and waiting for all to complete
     * (when {@code preserveOrder} is false), ignoring empty or {@code null} input.
     *
     * @param outs          the messages to deliver; {@code null} or empty lists result in no action
     * @param preserveOrder whether to send messages one-by-one in the given order ({@code true}) or dispatch
     *                      them concurrently and wait for completion ({@code false})
     */
    public void sendBatch(List<MessageOut> outs, boolean preserveOrder) {
        if (outs == null || outs.isEmpty()) return;
        if (preserveOrder) {
            for (MessageOut out : outs) send(out);
        } else {
            CompletableFuture<?>[] fs = outs.stream().map(this::sendAsync).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(fs).join();
        }
    }

    @PreDestroy
    public void shutdown() {
        sendPool.shutdown();
    }
}
