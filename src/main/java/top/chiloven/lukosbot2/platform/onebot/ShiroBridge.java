package top.chiloven.lukosbot2.platform.onebot;

import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.model.Address;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Shiro
@Component
@RequiredArgsConstructor
public class ShiroBridge {

    private final MessageDispatcher dispatcher;

    @PrivateMessageHandler
    public void onPrivate(Bot bot, PrivateMessageEvent e) {
        MessageIn in = new MessageIn(
                new Address(ChatPlatform.ONEBOT, e.getUserId(), false),
                e.getUserId(),
                e.getMessage() == null ? "" : e.getMessage()
        );
        dispatcher.receive(in);
    }

    @GroupMessageHandler
    public void onGroup(Bot bot, GroupMessageEvent e) {
        MessageIn in = new MessageIn(
                new Address(ChatPlatform.ONEBOT, e.getGroupId(), true),
                e.getUserId(),
                e.getMessage() == null ? "" : e.getMessage()
        );
        dispatcher.receive(in);
    }
}
