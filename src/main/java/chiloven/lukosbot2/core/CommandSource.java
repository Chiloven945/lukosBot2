package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

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

    // ==== 便捷附件方法（基于当前会话地址 in.addr()） ====

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
}
