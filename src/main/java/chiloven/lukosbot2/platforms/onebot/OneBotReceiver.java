package chiloven.lukosbot2.platforms.onebot;

import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.ChatPlatform;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.spi.Receiver;
import chiloven.lukosbot2.spi.Sender;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class OneBotReceiver implements Receiver {
    private final String url, token;
    private OneBotStack stack;                   // ← 内部持有
    private Consumer<MessageIn> sink = __ -> {
    };

    public OneBotReceiver(String wsUrl, String accessToken) {
        this.url = wsUrl;
        this.token = accessToken;
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.ONEBOT;
    }

    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
    }

    @Override
    public void start() {
        if (stack != null) return;
        stack = new OneBotStack(url, token, new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket w, CharSequence d, boolean last) {
                buf.append(d);
                if (last) {
                    String raw = buf.toString();
                    buf.setLength(0);
                    try {
                        JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
                        if (!o.has("post_type") || !"message".equals(o.get("post_type").getAsString())) return null;
                        boolean group = "group".equals(o.get("message_type").getAsString());
                        long chatId = group ? o.get("group_id").getAsLong() : o.get("user_id").getAsLong();
                        Long userId = o.has("user_id") ? o.get("user_id").getAsLong() : null;
                        String text = o.has("message") ? o.get("message").getAsString() : "";
                        sink.accept(new MessageIn(new Address(ChatPlatform.ONEBOT, chatId, group), userId, text));
                    } catch (Exception ignored) {
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void stop() {
        if (stack != null) stack.close();
    }

    /**
     * 复用同一条连接返回 Sender（避免 Main 触碰 Stack）
     */
    public Sender sender() {
        if (stack == null) start();
        return new OneBotSender(stack);
    }
}
