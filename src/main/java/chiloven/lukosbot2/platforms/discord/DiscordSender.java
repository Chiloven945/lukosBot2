package chiloven.lukosbot2.platforms.discord;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.model.OutContentType;
import chiloven.lukosbot2.platforms.Sender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
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

    private static String buildContent(MessageOut out, boolean includeImageUrls) {
        StringBuilder sb = new StringBuilder();
        if (out.text() != null && !out.text().isBlank()) sb.append(out.text());
        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                if (a.url() != null && a.bytes() == null) {
                    boolean isImageUrl = a.type() == OutContentType.IMAGE;
                    if (!isImageUrl || includeImageUrls) {
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append(a.url());
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void send(MessageOut out) {
        List<FileUpload> uploads = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();

        if (out.attachments() != null) {
            for (Attachment a : out.attachments()) {
                // 1) 有字节 → 走文件上传
                if (a.bytes() != null) {
                    String name = (a.name() == null || a.name().isBlank())
                            ? (a.type() == OutContentType.IMAGE ? "image.bin" : "file.bin")
                            : a.name();
                    uploads.add(FileUpload.fromData(new ByteArrayInputStream(a.bytes()), name));
                } else if (a.type() == OutContentType.IMAGE && a.url() != null && !a.url().isBlank()) {
                    // 2) 只有URL的图片 → 作为 Embed 图片发送
                    embeds.add(new EmbedBuilder().setImage(a.url()).build());
                }
            }
        }

        String content = buildContent(out, /* includeImageUrls */ false);

        if (out.addr().group()) {
            TextChannel ch = stack.jda.getTextChannelById(out.addr().chatId());
            if (ch == null) return;

            if (uploads.isEmpty()) {
                // 只有文字 + embeds
                ch.sendMessage(content).setEmbeds(embeds).queue();
            } else {
                // 文件 + 文字 + embeds
                ch.sendFiles(uploads).setContent(content).setEmbeds(embeds).queue();
            }
        } else {
            long userId = out.addr().chatId();
            stack.jda.retrieveUserById(userId)
                    .flatMap(User::openPrivateChannel)
                    .flatMap(pc -> {
                        if (uploads.isEmpty()) {
                            return pc.sendMessage(content).setEmbeds(embeds);
                        } else {
                            return pc.sendFiles(uploads).setContent(content).setEmbeds(embeds);
                        }
                    }).queue();
        }
    }
}
