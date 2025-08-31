package chiloven.lukosbot2.platforms.onebot;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.model.OutContentType;
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
        // === 文本 + 图片（以 CQ 码拼接在 message 内） ===
        StringBuilder msg = new StringBuilder();
        if (out.text() != null && !out.text().isBlank()) msg.append(out.text());

        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                if (a.type() == OutContentType.IMAGE && a.url() != null) {
                    msg.append("[CQ:image,file=").append(a.url()).append("]");
                }
            }
        }

        JsonObject req = new JsonObject();
        req.addProperty("action", "send_msg");
        JsonObject p = new JsonObject();
        p.addProperty("message", msg.toString());
        if (out.addr().group()) {
            p.addProperty("message_type", "group");
            p.addProperty("group_id", out.addr().chatId());
        } else {
            p.addProperty("message_type", "private");
            p.addProperty("user_id", out.addr().chatId());
        }
        req.add("params", p);
        stack.ws.sendText(req.toString(), true);

        // === 文件（URL 上传；bytes 场景需另行实现落盘或 HTTP 上传） ===
        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                if (a.type() == OutContentType.FILE && a.url() != null) {
                    JsonObject up = new JsonObject();
                    JsonObject params = new JsonObject();
                    String name = (a.name() == null || a.name().isBlank()) ? "file.bin" : a.name();
                    params.addProperty("name", name);
                    params.addProperty("url", a.url());
                    if (out.addr().group()) {
                        up.addProperty("action", "upload_group_file");
                        params.addProperty("group_id", out.addr().chatId());
                    } else {
                        up.addProperty("action", "upload_private_file");
                        params.addProperty("user_id", out.addr().chatId());
                    }
                    up.add("params", params);
                    stack.ws.sendText(up.toString(), true);
                }
            }
        }
    }
}
