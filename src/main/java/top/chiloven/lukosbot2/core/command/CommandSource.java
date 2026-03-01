package top.chiloven.lukosbot2.core.command;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.*;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.outbound.OutFile;
import top.chiloven.lukosbot2.model.message.outbound.OutImage;
import top.chiloven.lukosbot2.model.message.outbound.OutPart;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.util.message.TextExtractor;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Unified execution context for both commands and services.
 *
 * <p>This class intentionally keeps the name {@code CommandSource} so it can be used
 * as the Brigadier command source type, while also replacing the old {@code ServiceContext}.</p>
 *
 * <p>A {@code CommandSource} may or may not have an associated inbound message:
 * for scheduled services, only the target {@link Address} is available.</p>
 */
@Accessors(fluent = true)
public final class CommandSource {

    @Getter
    private final Address addr;
    @Getter
    private final InboundMessage inbound;
    private final Consumer<OutboundMessage> sink;

    private CommandSource(
            @NonNull Address addr,
            @Nullable InboundMessage inbound,
            @NonNull Consumer<OutboundMessage> sink
    ) {
        this.addr = addr;
        this.inbound = inbound;
        this.sink = sink;
    }

    /**
     * Create a source for an inbound message.
     */
    public static CommandSource forInbound(
            @NonNull InboundMessage in,
            Consumer<OutboundMessage> sink
    ) {
        Objects.requireNonNull(in.addr(), "in.addr");
        return new CommandSource(in.addr(), in, sink);
    }

    /**
     * Create a source for a known address without an inbound message (e.g. service tick).
     */
    public static CommandSource forAddress(
            Address addr,
            Consumer<OutboundMessage> sink
    ) {
        return new CommandSource(addr, null, sink);
    }

    public Chat chat() {
        return inbound == null ? new Chat(addr, null) : inbound.chat();
    }

    public MessageMeta meta() {
        return inbound == null ? MessageMeta.empty() : inbound.meta();
    }

    public List<InPart> parts() {
        return inbound == null ? List.of() : inbound.partsSafe();
    }

    /**
     * Extract the "primary" text from the inbound message.
     *
     * <p>Rules are implemented in {@link TextExtractor} (caption first, otherwise first text part).</p>
     */
    public String primaryText() {
        return inbound == null ? "" : TextExtractor.primaryText(inbound);
    }

    /**
     * Returns user id, or 0 if missing.
     *
     * <p>Prefer {@link #userIdOrNull()} when you need to distinguish missing IDs.</p>
     */
    public long userId() {
        Long id = userIdOrNull();
        return id == null ? 0L : id;
    }

    /**
     * Returns user id if present, otherwise {@code null}.
     */
    public Long userIdOrNull() {
        return sender().id();
    }

    public Sender sender() {
        return inbound == null ? Sender.unknown() : inbound.sender();
    }

    public long chatId() {
        return addr.chatId();
    }

    public boolean isGroup() {
        return addr.group();
    }

    // ---- reply helpers ----

    public void reply(String text) {
        if (text == null) return;
        sink.accept(OutboundMessage.text(addr, text));
    }

    public void reply(OutboundMessage out) {
        if (out == null) return;
        sink.accept(out);
    }

    public void replyParts(List<OutPart> parts) {
        if (parts == null || parts.isEmpty()) return;
        sink.accept(new OutboundMessage(addr, parts));
    }

    public void replyImage(MediaRef ref, String caption) {
        if (ref == null) return;
        sink.accept(new OutboundMessage(
                addr,
                List.of(new OutImage(ref, caption, null, null))
        ));
    }

    public void replyFile(MediaRef ref, String name, String caption) {
        if (ref == null) return;
        sink.accept(new OutboundMessage(
                addr,
                List.of(new OutFile(ref, name, null, caption))
        ));
    }

    /**
     * Send a message to another address using the same sink.
     *
     * <p>This is useful for services or admin commands that broadcast to different chats.</p>
     */
    public void send(Address to, OutboundMessage out) {
        if (to == null || out == null) return;
        sink.accept(new OutboundMessage(to, out.parts()));
    }

    public void send(Address to, String text) {
        if (to == null || text == null) return;
        sink.accept(OutboundMessage.text(to, text));
    }

}
