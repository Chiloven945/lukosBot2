package top.chiloven.lukosbot2.platform.onebot;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.*;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Shiro
@Component
@RequiredArgsConstructor
public class ShiroBridge {

    private final MessageDispatcher dispatcher;

    @PrivateMessageHandler
    public void onPrivate(Bot bot, PrivateMessageEvent e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        Address addr = new Address(ChatPlatform.ONEBOT, e.getUserId(), false);

        Sender sender = new Sender(e.getUserId(), null, null, false);
        Chat chat = new Chat(addr, null);
        MessageMeta meta = MessageMeta.empty();

        List<InPart> parts = parseCqParts(raw);
        if (parts.isEmpty() && !raw.isBlank()) {
            parts = List.of(new InText(raw));
        }

        InboundMessage in = new InboundMessage(addr, sender, chat, meta, parts, Map.of("raw", raw));
        dispatcher.receive(in);
    }

    /**
     * Extremely small CQ-code parser.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>[CQ:image,url=http://...]
     *   <li>[CQ:image,file=xxxx,url=http://...]
     * </ul>
     */
    static List<InPart> parseCqParts(String message) {
        if (message == null || message.isEmpty()) return List.of();

        List<InPart> parts = new ArrayList<>();
        int i = 0;
        while (i < message.length()) {
            int start = message.indexOf("[CQ:", i);
            if (start < 0) {
                String tail = message.substring(i);
                if (!tail.isBlank()) parts.add(new InText(tail));
                break;
            }

            // text before CQ
            if (start > i) {
                String t = message.substring(i, start);
                if (!t.isBlank()) parts.add(new InText(t));
            }

            int end = message.indexOf(']', start);
            if (end < 0) {
                // malformed, treat rest as text
                String rest = message.substring(start);
                if (!rest.isBlank()) parts.add(new InText(rest));
                break;
            }

            String cq = message.substring(start + 4, end); // after "[CQ:"
            parseOneCq(cq, parts);

            i = end + 1;
        }
        return parts;
    }

    private static void parseOneCq(String cq, List<InPart> parts) {
        if (cq == null || cq.isBlank()) return;

        String[] seg = cq.split(",");
        if (seg.length == 0) return;

        String type = seg[0].trim();

        // parse key-values
        java.util.Map<String, String> kv = new java.util.LinkedHashMap<>();
        for (int j = 1; j < seg.length; j++) {
            String s = seg[j];
            int eq = s.indexOf('=');
            if (eq <= 0) continue;
            String k = s.substring(0, eq).trim();
            String v = s.substring(eq + 1).trim();
            kv.put(k, v);
        }

        switch (type) {
            case "image" -> {
                String url = kv.get("url");
                String file = kv.get("file");
                MediaRef ref = null;
                if (url != null && !url.isBlank()) {
                    ref = new UrlRef(url);
                } else if (file != null && !file.isBlank()) {
                    ref = new PlatformFileRef("onebot", file);
                }
                if (ref != null) {
                    parts.add(new InImage(ref, null, file, null));
                }
            }
            case "file" -> {
                String url = kv.get("url");
                String name = kv.get("name");
                String file = kv.get("file");
                MediaRef ref = null;
                if (url != null && !url.isBlank()) {
                    ref = new UrlRef(url);
                } else if (file != null && !file.isBlank()) {
                    ref = new PlatformFileRef("onebot", file);
                }
                if (ref != null) {
                    parts.add(new InFile(ref, name != null ? name : file, null, null, null));
                }
            }
            case "at" -> {
                // Represent @ as plain text to preserve meaning for commands/services.
                String qq = kv.get("qq");
                if (qq != null && !qq.isBlank()) {
                    parts.add(new InText("@" + qq));
                }
            }
            default -> {
                // ignore unknown
            }
        }
    }

    @GroupMessageHandler
    public void onGroup(Bot bot, GroupMessageEvent e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        Address addr = new Address(ChatPlatform.ONEBOT, e.getGroupId(), true);

        Sender sender = new Sender(e.getUserId(), null, null, false);
        Chat chat = new Chat(addr, null);
        MessageMeta meta = MessageMeta.empty();

        List<InPart> parts = parseCqParts(raw);
        if (parts.isEmpty() && !raw.isBlank()) {
            parts = List.of(new InText(raw));
        }

        InboundMessage in = new InboundMessage(addr, sender, chat, meta, parts, Map.of("raw", raw));
        dispatcher.receive(in);
    }

}
