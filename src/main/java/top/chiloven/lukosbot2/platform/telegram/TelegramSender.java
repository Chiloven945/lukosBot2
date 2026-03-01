package top.chiloven.lukosbot2.platform.telegram;

import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
 * Telegram sender that translates {@link OutboundMessage} into Telegram API calls.
 *
 * <p>Supports ordered, mixed content via {@link OutboundMessage#parts()}:
 * text, image and file. When {@link DeliveryHints#preferCaption()} is enabled, the sender will try to merge an adjacent
 * text part into a media caption for a better user experience.</p>
 */
public final class TelegramSender implements ISender {

    private final TelegramStack stack;

    public TelegramSender(TelegramStack stack) {
        this.stack = stack;
    }

    private static InputFile toInputFile(MediaRef ref, String name, String mime) {
        switch (ref) {
            case null -> {
                return new InputFile("about:blank");
            }
            case BytesRef b -> {
                String n = pickName(name, b.name(), mime);
                return new InputFile(new ByteArrayInputStream(b.bytes()), n);
            }
            case UrlRef(String url) -> {
                return new InputFile(url);
            }
            case PlatformFileRef p -> {
                // Telegram accepts file_id in the same field.
                return new InputFile(p.fileId());
                // Telegram accepts file_id in the same field.
            }
            default -> {
            }
        }

        return new InputFile("about:blank");
    }

    private static String pickName(String preferred, String fallback, String mime) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;

        if (mime != null && mime.toLowerCase().contains("image")) return "image.bin";
        return "file.bin";
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

        String chatId = String.valueOf(out.addr().chatId());
        List<OutPart> parts = out.parts() == null ? Collections.emptyList() : out.parts();
        if (parts.isEmpty()) return;

        List<OutPart> normalized = mergeTextParts(parts);

        boolean preferCaption = out.hints() != null && out.hints().preferCaption();

        for (int i = 0; i < normalized.size(); i++) {
            OutPart p = normalized.get(i);
            switch (p) {
                case OutText(String text1) -> {
                    String text = safe(text1);
                    if (text.isBlank()) continue;

                    // If next part is media and prefers caption, try to attach this text as caption.
                    if (preferCaption && i + 1 < normalized.size()) {
                        OutPart next = normalized.get(i + 1);
                        if (next instanceof OutImage img && (img.caption() == null || img.caption().isBlank())) {
                            sendPhoto(chatId, img, text);
                            i++; // consume next
                            continue;
                        }
                        if (next instanceof OutFile f && (f.caption() == null || f.caption().isBlank())) {
                            sendDocument(chatId, f, text);
                            i++; // consume next
                            continue;
                        }
                    }

                    sendText(chatId, text);
                }
                case OutImage img -> {
                    sendPhoto(chatId, img, safe(img.caption()));
                }
                case OutFile f -> sendDocument(chatId, f, safe(f.caption()));
                case null, default -> {
                }
            }

        }
    }

    private void sendText(String chatId, String text) {
        if (text == null || text.isBlank()) return;
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        stack.execute(sm);
    }

    private void sendPhoto(String chatId, OutImage img, String caption) {
        if (img == null) return;
        SendPhoto sp = SendPhoto.builder()
                .chatId(chatId)
                .photo(toInputFile(img.ref(), img.name(), img.mime()))
                .caption(caption == null || caption.isBlank() ? null : caption)
                .build();
        stack.execute(sp);
    }

    private void sendDocument(String chatId, OutFile f, String caption) {
        if (f == null) return;
        SendDocument sd = SendDocument.builder()
                .chatId(chatId)
                .document(toInputFile(f.ref(), f.name(), f.mime()))
                .caption(caption == null || caption.isBlank() ? null : caption)
                .build();
        stack.execute(sd);
    }

}
