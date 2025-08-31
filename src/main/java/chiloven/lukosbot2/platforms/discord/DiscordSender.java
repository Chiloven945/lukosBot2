package chiloven.lukosbot2.platforms.discord;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.model.OutContentType;
import chiloven.lukosbot2.spi.Sender;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public final class DiscordSender implements Sender {
    private final DiscordStack stack;

    public DiscordSender(DiscordStack stack) {
        this.stack = stack;
    }

    private static String buildContent(MessageOut out) {
        StringBuilder sb = new StringBuilder();
        if (out.text() != null && !out.text().isBlank()) sb.append(out.text());
        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                if (a.url() != null && a.bytes() == null) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(a.url()); // 贴出 URL，Discord 会自动预览
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void send(MessageOut out) {
        List<FileUpload> uploads = new ArrayList<>();

        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                if (a.bytes() != null) {
                    String name = (a.name() == null || a.name().isBlank())
                            ? (a.type() == OutContentType.IMAGE ? "image.bin" : "file.bin")
                            : a.name();
                    uploads.add(FileUpload.fromData(new ByteArrayInputStream(a.bytes()), name));
                }
            }
        }

        String content = buildContent(out);

        if (out.addr().group()) {
            TextChannel ch = stack.jda.getTextChannelById(out.addr().chatId());
            if (ch == null) return;
            if (uploads.isEmpty()) ch.sendMessage(content).queue();
            else ch.sendFiles(uploads).setContent(content).queue();
        } else {
            long userId = out.addr().chatId();
            if (uploads.isEmpty()) {
                stack.jda.retrieveUserById(userId)
                        .flatMap(User::openPrivateChannel)
                        .flatMap(pc -> pc.sendMessage(content)).queue();
            } else {
                stack.jda.retrieveUserById(userId)
                        .flatMap(User::openPrivateChannel)
                        .flatMap(pc -> pc.sendFiles(uploads).setContent(content)).queue();
            }
        }
    }
}
