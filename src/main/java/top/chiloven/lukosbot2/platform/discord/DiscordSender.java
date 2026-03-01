package top.chiloven.lukosbot2.platform.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import top.chiloven.lukosbot2.model.message.media.BytesRef;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
import top.chiloven.lukosbot2.model.message.outbound.*;
import top.chiloven.lukosbot2.platform.ISender;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discord sender that translates {@link OutboundMessage} into Discord API calls.
 *
 * <p>Discord does not have a true "caption" concept for images like Telegram does. To preserve
 * the ordering semantics of {@link OutboundMessage#parts()}, this sender sends parts sequentially: each
 * {@link OutText}, {@link OutImage}, {@link OutFile} is sent as one Discord message (or a small sequence if the text is
 * too long).</p>
 */
public final class DiscordSender implements ISender {

    private static final int MAX_CONTENT = 2000;

    private final DiscordStack stack;

    public DiscordSender(DiscordStack stack) {
        this.stack = stack;
    }

    private static String pickName(String preferred, String fallback, String mime, boolean imageDefault) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;

        if (mime != null && mime.toLowerCase().contains("image")) return "image.bin";
        return imageDefault ? "image.bin" : "file.bin";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static List<OutPart> mergeTextParts(List<OutPart> parts) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<OutPart> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (OutPart p : parts) {
            if (p instanceof OutText(String text)) {
                String tx = safe(text);
                if (!tx.isBlank()) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(tx);
                }
                continue;
            }

            flushText(sb, out);
            if (p != null) out.add(p);
        }

        flushText(sb, out);
        return out;
    }

    private static void flushText(StringBuilder sb, List<OutPart> out) {
        if (sb == null || out == null) return;
        if (sb.isEmpty()) return;
        out.add(new OutText(sb.toString()));
        sb.setLength(0);
    }

    @Override
    public void send(OutboundMessage out) {
        if (out == null) return;

        List<OutPart> parts = out.parts() == null ? Collections.emptyList() : out.parts();
        if (parts.isEmpty()) return;

        List<OutPart> normalized = mergeTextParts(parts);

        if (out.addr().group()) {
            TextChannel ch = stack.jda.getTextChannelById(out.addr().chatId());
            if (ch == null) return;
            sendParts(ch, normalized);
            return;
        }

        long userId = out.addr().chatId();
        try {
            User u = stack.jda.retrieveUserById(userId).complete();
            if (u == null) return;
            MessageChannel pc = u.openPrivateChannel().complete();
            if (pc == null) return;
            sendParts(pc, normalized);
        } catch (Exception ignored) {
            // ignore errors opening DMs
        }
    }

    private void sendParts(MessageChannel ch, List<OutPart> parts) {
        if (ch == null || parts == null || parts.isEmpty()) return;

        for (OutPart p : parts) {
            switch (p) {
                case OutText(String text) -> {
                    sendTextChunks(ch, safe(text));
                }
                case OutImage img -> {
                    sendImagePart(ch, img);
                }
                case OutFile f -> sendFilePart(ch, f);
                case null, default -> {
                }
            }
        }
    }

    private void sendTextChunks(MessageChannel ch, String text) {
        if (text == null || text.isBlank()) return;

        // Discord content limit: 2000 chars. Split into chunks.
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + MAX_CONTENT);
            String chunk = text.substring(i, end);
            ch.sendMessage(chunk).complete();
            i = end;
        }
    }

    private void sendImagePart(MessageChannel ch, OutImage img) {
        if (img == null || img.ref() == null) return;

        String caption = safe(img.caption());
        MediaRef ref = img.ref();

        if (ref instanceof BytesRef b) {
            FileUpload upload = FileUpload.fromData(new ByteArrayInputStream(b.bytes()), pickName(img.name(), b.name(), img.mime(), true));
            if (caption.isBlank()) {
                ch.sendFiles(upload).complete();
            } else {
                sendWithOptionalUpload(ch, caption, List.of(upload), List.of());
            }
            return;
        }

        if (ref instanceof UrlRef(String url)) {
            MessageEmbed embed = new EmbedBuilder().setImage(url).build();
            sendWithOptionalUpload(ch, caption, List.of(), List.of(embed));
            return;
        }

        if (ref instanceof PlatformFileRef(String platform, String fileId)) {
            // Not directly usable on Discord; fall back to showing the identifier.
            String msg = caption.isBlank()
                    ? ("[image ref] " + platform + ":" + fileId)
                    : (caption + "\n[image ref] " + platform + ":" + fileId);
            sendTextChunks(ch, msg);
        }
    }

    private void sendFilePart(MessageChannel ch, OutFile f) {
        if (f == null || f.ref() == null) return;

        String caption = safe(f.caption());
        MediaRef ref = f.ref();

        switch (ref) {
            case BytesRef b -> {
                FileUpload upload = FileUpload.fromData(new ByteArrayInputStream(b.bytes()), pickName(f.name(), b.name(), f.mime(), false));
                sendWithOptionalUpload(ch, caption, List.of(upload), List.of());
            }
            case UrlRef(String url) -> {
                String msg = caption.isBlank() ? url : (caption + "\n" + url);
                sendTextChunks(ch, msg);
            }
            case PlatformFileRef(String platform, String fileId) -> {
                String msg = caption.isBlank()
                        ? ("[file ref] " + platform + ":" + fileId)
                        : (caption + "\n[file ref] " + platform + ":" + fileId);
                sendTextChunks(ch, msg);
            }
            default -> {
            }
        }
    }

    private void sendWithOptionalUpload(
            MessageChannel ch,
            String content,
            List<FileUpload> uploads,
            List<MessageEmbed> embeds
    ) {
        String c = safe(content);

        // If content is too long, send content first (chunked), then embeds/files.
        if (c.length() > MAX_CONTENT) {
            sendTextChunks(ch, c);
            c = "";
        }

        boolean hasUploads = uploads != null && !uploads.isEmpty();
        boolean hasEmbeds = embeds != null && !embeds.isEmpty();

        if (!hasUploads && !hasEmbeds) {
            if (!c.isBlank()) ch.sendMessage(c).complete();
            return;
        }

        if (hasUploads) {
            var action = ch.sendFiles(uploads);
            action.setContent(c.isBlank() ? "" : c);
            if (hasEmbeds) action.setEmbeds(embeds);
            action.complete();
            return;
        }

        // embeds only
        ch.sendMessage(c.isBlank() ? "" : c).setEmbeds(embeds).complete();
    }

}
