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
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.util.message.TextExtractor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Unified execution context for both commands and services.
 *
 * <p>This class intentionally keeps the name {@code CommandSource} so it can be used
 * as the Brigadier command source type, while also replacing the old service-specific context object.</p>
 *
 * <p>A {@code CommandSource} may or may not have an associated inbound message:
 * for scheduled services or synthetic triggers, only the target {@link Address} is available. All helper accessors are
 * therefore designed to fail soft and return sensible empty/default values when no inbound payload exists.</p>
 *
 * <p>The source also carries a shared outbound sink, which makes it possible for commands and services
 * to reply to the current chat or send messages to other chats without depending on a platform-specific sender.</p>
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
     * Creates a source bound to a concrete inbound message.
     *
     * @param in   inbound message.
     * @param sink outbound delivery sink.
     * @return source backed by the inbound message.
     */
    public static CommandSource forInbound(
            @NonNull InboundMessage in,
            Consumer<OutboundMessage> sink
    ) {
        Objects.requireNonNull(in.addr(), "in.addr");
        return new CommandSource(in.addr(), in, sink);
    }

    /**
     * Creates a source for a known address without an inbound message.
     *
     * <p>This is mainly used by time-based services, manual triggers, or fan-out operations where a
     * target chat is known but there is no live message to read metadata from.</p>
     *
     * @param addr target address.
     * @param sink outbound delivery sink.
     * @return source with {@link #inbound()} equal to {@code null}.
     */
    public static CommandSource forAddress(
            Address addr,
            Consumer<OutboundMessage> sink
    ) {
        return new CommandSource(addr, null, sink);
    }

    /**
     * Returns the logical chat wrapper for this execution.
     *
     * @return the inbound chat object when available, otherwise a synthetic chat built from {@link #addr()}.
     */
    public Chat chat() {
        return inbound == null ? new Chat(addr, null) : inbound.chat();
    }

    /**
     * Returns inbound message metadata.
     *
     * @return the inbound metadata, or {@link MessageMeta#empty()} when this source is synthetic.
     */
    public MessageMeta meta() {
        return inbound == null ? MessageMeta.empty() : inbound.meta();
    }

    /**
     * Returns safe inbound parts.
     *
     * @return the inbound parts list, or an empty list if no inbound message exists.
     */
    public List<InPart> parts() {
        return inbound == null ? List.of() : inbound.partsSafe();
    }

    /**
     * Returns the quoted / replied-to message when present.
     */
    public QuotedMessage quoted() {
        return inbound == null ? null : inbound.quoted();
    }

    /**
     * Looks up one extra metadata value attached to the inbound message.
     *
     * <p>Extra values are platform-specific hints such as slash-command markers, platform admin flags,
     * raw message payloads, or other adapter-provided data.</p>
     *
     * @param key metadata key.
     * @return the extra value, or {@code null} if absent.
     */
    public Object ext(String key) {
        return ext().get(key);
    }

    /**
     * Returns the full extra metadata map attached to the inbound message.
     *
     * @return platform-specific metadata, or an immutable empty map for synthetic sources.
     */
    public Map<String, Object> ext() {
        return inbound == null ? Collections.emptyMap() : inbound.ext();
    }

    /**
     * Returns the platform of the current address.
     *
     * @return chat platform derived from {@link #addr()}.
     */
    public ChatPlatform platform() {
        return addr.platform();
    }

    /**
     * Extracts the "primary" text from the inbound message.
     *
     * <p>Rules are implemented in {@link TextExtractor} (caption first, otherwise first text part).</p>
     *
     * @return primary text content, or an empty string if no inbound message exists.
     */
    public String primaryText() {
        return inbound == null ? "" : TextExtractor.primaryText(inbound);
    }

    /**
     * Returns the sender id, or {@code 0} when it is unavailable.
     *
     * <p>Prefer {@link #userIdOrNull()} when you need to distinguish between "missing" and a literal zero value.</p>
     *
     * @return sender id or {@code 0}.
     */
    public long userId() {
        Long id = userIdOrNull();
        return id == null ? 0L : id;
    }

    /**
     * Returns the sender id if present.
     *
     * @return sender id, or {@code null} when unavailable.
     */
    public Long userIdOrNull() {
        return sender().id();
    }

    /**
     * Returns the sender descriptor.
     *
     * @return inbound sender, or {@link Sender#unknown()} for synthetic sources.
     */
    public Sender sender() {
        return inbound == null ? Sender.unknown() : inbound.sender();
    }

    /**
     * Returns the numeric chat identifier.
     *
     * @return current chat id.
     */
    public long chatId() {
        return addr.chatId();
    }

    /**
     * Returns whether the current address represents a group-like context.
     *
     * @return {@code true} for group/guild/channel-style contexts; otherwise {@code false}.
     */
    public boolean isGroup() {
        return addr.group();
    }

    // ---- reply helpers ----

    /**
     * Sends a plain-text reply to the current address.
     *
     * @param text reply text; ignored when {@code null}.
     */
    public void reply(String text) {
        if (text == null) return;
        sink.accept(OutboundMessage.text(addr, text));
    }

    /**
     * Sends a prebuilt outbound message to the current address.
     *
     * @param out outbound message; ignored when {@code null}.
     */
    public void reply(OutboundMessage out) {
        if (out == null) return;
        sink.accept(out);
    }

    /**
     * Sends a multi-part reply to the current address.
     *
     * @param parts outbound parts; ignored when {@code null} or empty.
     */
    public void replyParts(List<OutPart> parts) {
        if (parts == null || parts.isEmpty()) return;
        sink.accept(new OutboundMessage(addr, parts));
    }

    /**
     * Sends an image reply to the current address.
     *
     * @param ref     media reference.
     * @param caption optional caption.
     */
    public void replyImage(MediaRef ref, String caption) {
        if (ref == null) return;
        sink.accept(new OutboundMessage(
                addr,
                List.of(new OutImage(ref, caption, null, null))
        ));
    }

    /**
     * Sends a file reply to the current address.
     *
     * @param ref     media reference.
     * @param name    file name visible to the user.
     * @param caption optional caption.
     */
    public void replyFile(MediaRef ref, String name, String caption) {
        if (ref == null) return;
        sink.accept(new OutboundMessage(
                addr,
                List.of(new OutFile(ref, name, null, caption))
        ));
    }

    /**
     * Sends a message to another address using the same sink.
     *
     * <p>This is useful for services or admin commands that broadcast to different chats.</p>
     *
     * @param to  destination address.
     * @param out outbound message.
     */
    public void send(Address to, OutboundMessage out) {
        if (to == null || out == null) return;
        sink.accept(new OutboundMessage(to, out.parts()));
    }

    /**
     * Sends a plain-text message to another address using the same sink.
     *
     * @param to   destination address.
     * @param text message text.
     */
    public void send(Address to, String text) {
        if (to == null || text == null) return;
        sink.accept(OutboundMessage.text(to, text));
    }

}
