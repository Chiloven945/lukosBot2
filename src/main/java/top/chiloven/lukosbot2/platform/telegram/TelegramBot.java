package top.chiloven.lukosbot2.platform.telegram;

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.*;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class TelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private final String username;

    private Consumer<InboundMessage> sink = _ -> {
    };

    public TelegramBot(String username) {
        this.username = username;
    }

    /**
     * Set message handler.
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    void setSink(Consumer<InboundMessage> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
    }

    @Override
    public void consume(Update u) {
        if (u == null) return;
        Message m = u.getMessage();
        if (m == null) return;

        long chatId = m.getChatId();
        boolean isGroup = m.getChat() != null && (m.getChat().isGroupChat() || m.getChat().isSuperGroupChat());

        Address addr = new Address(ChatPlatform.TELEGRAM, chatId, isGroup);

        // sender
        User from = m.getFrom();
        Sender sender = (from == null)
                ? Sender.unknown()
                : new Sender(from.getId(), from.getUserName(),
                joinName(from.getFirstName(), from.getLastName()),
                from.getIsBot());

        // chat info
        String title = (m.getChat() != null) ? m.getChat().getTitle() : null;
        Chat chat = new Chat(addr, title);

        // meta
        String msgId = (m.getMessageId() == null) ? null : String.valueOf(m.getMessageId());
        Long tsMs = (m.getDate() == null) ? null : (m.getDate().longValue() * 1000L);
        String replyToId = (m.getReplyToMessage() == null || m.getReplyToMessage().getMessageId() == null)
                ? null
                : String.valueOf(m.getReplyToMessage().getMessageId());
        MessageMeta meta = new MessageMeta(msgId, tsMs, replyToId, null);

        // parts
        List<InPart> parts = new ArrayList<>();

        String text = m.getText();
        if (text != null && !text.isBlank()) {
            parts.add(new InText(text));
        }

        String caption = m.getCaption();

        // photo
        if (m.getPhoto() != null && !m.getPhoto().isEmpty()) {
            PhotoSize best = pickLargest(m.getPhoto());
            if (best != null && best.getFileId() != null) {
                parts.add(new InImage(new PlatformFileRef("telegram", best.getFileId()), caption, null, null));
            }
        }

        // document
        Document doc = m.getDocument();
        if (doc != null && doc.getFileId() != null) {
            Long size = (doc.getFileSize() == null) ? null : doc.getFileSize();
            parts.add(new InFile(new PlatformFileRef("telegram", doc.getFileId()), doc.getFileName(), doc.getMimeType(), size, caption));
        }

        // (Other message types can be added later: audio/voice/video/sticker/location...)

        InboundMessage in = new InboundMessage(addr, sender, chat, meta, parts, Map.of(
                "updateId", u.getUpdateId(),
                "botUsername", username
        ));

        sink.accept(in);
    }

    private static String joinName(String first, String last) {
        String f = (first == null) ? "" : first.trim();
        String l = (last == null) ? "" : last.trim();
        String s = (f + " " + l).trim();
        return s.isEmpty() ? null : s;
    }

    private static PhotoSize pickLargest(List<PhotoSize> photos) {
        PhotoSize best = null;
        int bestArea = -1;
        for (PhotoSize p : photos) {
            if (p == null) continue;
            int w = (p.getWidth() == null) ? 0 : p.getWidth();
            int h = (p.getHeight() == null) ? 0 : p.getHeight();
            int area = w * h;
            if (area > bestArea) {
                bestArea = area;
                best = p;
            }
        }
        return best != null ? best : (photos.isEmpty() ? null : photos.getLast());
    }

}
