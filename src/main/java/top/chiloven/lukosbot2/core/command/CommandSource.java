package top.chiloven.lukosbot2.core.command;

import top.chiloven.lukosbot2.model.Address;
import top.chiloven.lukosbot2.model.Attachment;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.model.MessageOut;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.function.Consumer;

public class CommandSource {
    private final MessageIn in;
    private final Consumer<MessageOut> sink;

    public CommandSource(MessageIn in, Consumer<MessageOut> sink) {
        this.in = in;
        this.sink = sink;
    }

    public MessageIn in() {
        return in;
    }

    // ===== Reply =====

    /**
     * Reply with a simple text message (no attachments).
     *
     * @param text the text to send
     */
    public void reply(String text) {
        sink.accept(MessageOut.text(in.addr(), text));
    }

    /**
     * Reply with a full MessageOut (can include attachments).
     *
     * @param out the MessageOut to send
     */
    public void reply(MessageOut out) {
        sink.accept(out);
    }

    /**
     * Reply with an image from a URL.
     *
     * @param url the image URL
     */
    public void replyImageUrl(String url) {
        sink.accept(MessageOut.text(in.addr(), null).with(Attachment.imageUrl(url)));
    }

    /**
     * Reply with an image from byte array.
     *
     * @param name  the image name (for filename)
     * @param bytes the image bytes
     * @param mime  the MIME type (e.g. "image/png")
     */
    public void replyImageBytes(String name, byte[] bytes, String mime) {
        sink.accept(MessageOut.text(in.addr(), null).with(Attachment.imageBytes(name, bytes, mime)));
    }

    /**
     * Reply with a file from a URL.
     *
     * @param name the file name (for filename)
     * @param url  the file URL
     */
    public void replyFileUrl(String name, String url) {
        sink.accept(MessageOut.text(in.addr(), null).with(Attachment.fileUrl(name, url)));
    }

    /**
     * Reply with a file from byte array.
     *
     * @param name  the file name (for filename)
     * @param bytes the file bytes
     * @param mime  the MIME type (e.g. "application/pdf")
     */
    public void replyFileBytes(String name, byte[] bytes, String mime) {
        sink.accept(MessageOut.text(in.addr(), null).with(Attachment.fileBytes(name, bytes, mime)));
    }

    // ===== Convenience for help/usage =====

    /**
     * Returns the command prefix for this chat.
     *
     * <p>Current implementation uses a fixed prefix {@code "/"}.
     * If you later support per-chat prefix, change it here and all help rendering will follow.</p>
     */
    public String prefix() {
        return "/";
    }

    /**
     * Returns the maximum safe text length for a single message on the current platform.
     *
     * <p>This is used by help/usage rendering to decide whether to send text or fall back to an image.</p>
     */
    public int maxTextLength() {
        return switch (platform()) {
            case TELEGRAM -> 3500; // Telegram text limit is larger, but keep some margin for markdown/formatting
            case DISCORD -> 1800;  // Discord message limit is 2000; keep margin
            case ONEBOT -> 1800;   // varies; keep conservative
        };
    }

    /**
     * Sends a PNG image as an image attachment.
     *
     * <p>Centralized helper to keep callers (HelpCommand, IBotCommand) consistent.</p>
     */
    public void sendImagePng(String filename, byte[] pngBytes) {
        replyImageBytes(filename, pngBytes, "image/png");
    }

    /**
     * Sends an arbitrary file attachment.
     */
    public void sendFile(String filename, byte[] bytes, String mime) {
        replyFileBytes(filename, bytes, mime);
    }

    // ===== Getters =====

    /**
     * ID of the user who sent this message.
     */
    public long userId() {
        return in.userId();
    }

    /**
     * Chat ID (group ID or private chat ID) of this message.
     */
    public long chatId() {
        return in.addr().chatId();
    }

    /**
     * Platform of this message (TELEGRAM / DISCORD / ONEBOT ...).
     */
    public ChatPlatform platform() {
        return in.addr().platform();
    }

    /**
     * Full address object of this message.
     */
    public Address addr() {
        return in.addr();
    }

    /**
     * Raw text content of this message.
     */
    public String text() {
        return in.text();
    }

}
