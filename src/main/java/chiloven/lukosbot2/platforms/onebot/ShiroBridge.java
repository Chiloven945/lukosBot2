package chiloven.lukosbot2.platforms.onebot;

import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platforms.ChatPlatform;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Shiro
@Component
public class ShiroBridge {

    private static final AtomicReference<Consumer<MessageIn>> SINK = new AtomicReference<>(__ -> {
    });

    /**
     * 由 OneBotReceiver.bind(...) 注入
     */
    public static void setSink(Consumer<MessageIn> sink) {
        SINK.set(Objects.requireNonNullElse(sink, __ -> {
        }));
    }

    @PrivateMessageHandler
    public void onPrivate(Bot bot, PrivateMessageEvent e) {
        MessageIn in = new MessageIn(
                new Address(ChatPlatform.ONEBOT, e.getUserId(), false),
                e.getUserId(),
                e.getMessage() == null ? "" : e.getMessage()
        );
        SINK.get().accept(in);
    }

    @GroupMessageHandler
    public void onGroup(Bot bot, GroupMessageEvent e) {
        MessageIn in = new MessageIn(
                new Address(ChatPlatform.ONEBOT, e.getGroupId(), true),
                e.getUserId(),
                e.getMessage() == null ? "" : e.getMessage()
        );
        SINK.get().accept(in);
    }
}
