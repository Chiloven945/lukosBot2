package top.chiloven.lukosbot2.platform.onebot;

import top.chiloven.lukosbot2.model.Attachment;
import top.chiloven.lukosbot2.model.MessageOut;
import top.chiloven.lukosbot2.model.OutContentType;
import top.chiloven.lukosbot2.platform.Sender;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OneBotSender implements Sender {

    private final BotContainer botContainer;

    public OneBotSender(BotContainer botContainer) {
        this.botContainer = botContainer;
    }

    @Override
    public void send(MessageOut out) {
        Bot bot = pickBot();
        if (bot == null || out == null || out.addr() == null) {
            return;
        }

        // Text + Image
        MsgUtils builder = MsgUtils.builder();
        if (out.text() != null && !out.text().isBlank()) {
            builder.text(out.text());
        }
        if (out.attachments() != null) {
            out.attachments().stream()
                    .filter(a -> a.type() == OutContentType.IMAGE
                            && a.url() != null && !a.url().isBlank())
                    .forEach(a -> builder.img(a.url()));
        }
        String message = builder.build();

        if (out.addr().group()) {
            bot.sendGroupMsg(out.addr().chatId(), message, false);
        } else {
            bot.sendPrivateMsg(out.addr().chatId(), message, false);
        }

        // File (optional)
        if (out.attachments() != null) {
            List<Attachment> files = out.attachments().stream()
                    .filter(a -> a.type() == OutContentType.FILE
                            && a.url() != null && !a.url().isBlank())
                    .toList();
            for (Attachment a : files) {
                Map<String, Object> params = new HashMap<>();
                params.put("name",
                        (a.name() == null || a.name().isBlank()) ? "file.bin" : a.name());
                params.put("url", a.url());
                if (out.addr().group()) {
                    params.put("group_id", out.addr().chatId());
                    bot.customRequest(() -> "upload_group_file", params);
                } else {
                    params.put("user_id", out.addr().chatId());
                    bot.customRequest(() -> "upload_private_file", params);
                }
            }
        }
    }

    /**
     * Pick first bot from container.
     */
    private Bot pickBot() {
        if (botContainer == null || botContainer.robots == null || botContainer.robots.isEmpty()) {
            return null;
        }
        return botContainer.robots.values().stream().findFirst().orElse(null);
    }
}
