package chiloven.lukosbot2.platforms.onebot;

import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import com.google.gson.JsonObject;

public final class OneBotSender implements Sender {
    private final OneBotStack stack;

    public OneBotSender(OneBotStack stack) {
        this.stack = stack;
    }

    /**
     * Send a message
     *
     * @param out message to send
     */
    @Override
    public void send(MessageOut out) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "send_msg");
        JsonObject p = new JsonObject();
        p.addProperty("message", out.text());
        if (out.addr().group()) {
            p.addProperty("message_type", "group");
            p.addProperty("group_id", out.addr().chatId());
        } else {
            p.addProperty("message_type", "private");
            p.addProperty("user_id", out.addr().chatId());
        }
        req.add("params", p);
        stack.ws.sendText(req.toString(), true);
    }
}
