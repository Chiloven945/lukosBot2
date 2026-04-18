package top.chiloven.lukosbot2.platform.onebot;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.*;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Shiro
@Component
@RequiredArgsConstructor
public class ShiroBridge {

    private final MessageDispatcher dispatcher;
    private final JsonMapper mapper;

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
        return parseCq(message).parts();
    }

    static ParseResult parseCq(String message) {
        if (message == null || message.isEmpty()) return new ParseResult(List.of(), null);

        List<InPart> parts = new ArrayList<>();
        String replyId = null;

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

            String cq = message.substring(start + 4, end);
            String maybeReplyId = parseOneCq(cq, parts);
            if (replyId == null && maybeReplyId != null && !maybeReplyId.isBlank()) {
                replyId = maybeReplyId;
            }
            i = end + 1;
        }
        return new ParseResult(parts, replyId);
    }

    private static String parseOneCq(String cq, List<InPart> parts) {
        if (cq == null || cq.isBlank()) return null;

        String[] seg = cq.split(",");
        if (seg.length == 0) return null;

        String type = seg[0].trim();

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
                return null;
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
                return null;
            }
            case "at" -> {
                String qq = kv.get("qq");
                if (qq != null && !qq.isBlank()) {
                    parts.add(new InText("@" + qq));
                }
                return null;
            }
            case "reply" -> {
                return kv.get("id");
            }
            default -> {
                return null;
            }
        }
    }

    @PrivateMessageHandler
    public void onPrivate(Bot bot, PrivateMessageEvent e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        Address addr = new Address(ChatPlatform.ONEBOT, e.getUserId(), false);

        Sender sender = new Sender(e.getUserId(), null, null, false);
        Chat chat = new Chat(addr, null);

        ParseResult parsed = parseCq(messageString(raw));
        List<InPart> parts = parsed.parts();
        if (parts.isEmpty() && !raw.isBlank()) {
            parts = List.of(new InText(raw));
        }

        MessageMeta meta = new MessageMeta(messageIdOf(e), timestampMsOf(e), parsed.replyId(), null);
        QuotedMessage quoted = resolveQuoted(bot, parsed.replyId());

        InboundMessage in = new InboundMessage(addr, sender, chat, meta, parts, Map.of("raw", raw), quoted);
        dispatcher.receive(in);
    }

    private static String messageString(String raw) {
        return raw == null ? "" : raw;
    }

    private static String messageIdOf(Object event) {
        Object value = invokeNoArg(event, "getMessageId");
        if (value == null) value = invokeNoArg(event, "getMessageSeq");
        return value == null ? null : String.valueOf(value);
    }

    private static Long timestampMsOf(Object event) {
        Object value = invokeNoArg(event, "getTime");
        if (value instanceof Number n) {
            return n.longValue() * 1000L;
        }
        return null;
    }

    private QuotedMessage resolveQuoted(Bot bot, String replyId) {
        if (bot == null || replyId == null || replyId.isBlank()) return null;
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("message_id", replyId);
            Object response = bot.customRequest(() -> "get_msg", params);
            JsonNode root = toNode(response);
            if (root == null || root.isNull()) return null;

            JsonNode data = root.has("data") ? root.get("data") : root;
            String messageId = textOrNull(data.path("message_id"));
            if (messageId == null || messageId.isBlank()) {
                messageId = replyId;
            }
            Long senderId = longOrNull(data.path("sender").path("user_id"));
            String message = textOrNull(data.path("message"));
            if (message == null) {
                message = textOrNull(data.path("raw_message"));
            }
            ParseResult parsed = parseCq(message);
            List<InPart> parts = parsed.parts();
            if (parts.isEmpty() && message != null && !message.isBlank()) {
                parts = List.of(new InText(message));
            }
            return new QuotedMessage(messageId, senderId, parts);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(name);
            return m.invoke(target);
        } catch (Exception _) {
            return null;
        }
    }

    private JsonNode toNode(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node) return node;
        try {
            if (value instanceof CharSequence seq) {
                return mapper.readTree(seq.toString());
            }
            return mapper.valueToTree(value);
        } catch (Exception _) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String text = node.asString(null);
        return text == null || text.isBlank() ? null : text;
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return node.asLong();
        } catch (Exception _) {
            return null;
        }
    }

    @GroupMessageHandler
    public void onGroup(Bot bot, GroupMessageEvent e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        Address addr = new Address(ChatPlatform.ONEBOT, e.getGroupId(), true);

        Sender sender = new Sender(e.getUserId(), null, null, false);
        Chat chat = new Chat(addr, null);

        ParseResult parsed = parseCq(messageString(raw));
        List<InPart> parts = parsed.parts();
        if (parts.isEmpty() && !raw.isBlank()) {
            parts = List.of(new InText(raw));
        }

        MessageMeta meta = new MessageMeta(messageIdOf(e), timestampMsOf(e), parsed.replyId(), null);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("raw", raw);
        String role = extractGroupRole(e);
        if (role != null) {
            ext.put("onebot.groupRole", role);
        }

        QuotedMessage quoted = resolveQuoted(bot, parsed.replyId());
        InboundMessage in = new InboundMessage(addr, sender, chat, meta, parts, ext, quoted);
        dispatcher.receive(in);
    }

    private static String extractGroupRole(GroupMessageEvent e) {
        String fromSender = invokeRole(invokeNoArg(e, "getSender"));
        if (fromSender != null) return fromSender;
        return invokeRole(e);
    }

    private static String invokeRole(Object target) {
        Object value = invokeNoArg(target, "getRole");
        if (value == null) return null;
        return String.valueOf(value);
    }

    record ParseResult(List<InPart> parts, String replyId) {

        ParseResult {
            if (parts == null) parts = List.of();
        }

    }

}
