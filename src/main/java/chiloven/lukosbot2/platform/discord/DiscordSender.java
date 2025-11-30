package chiloven.lukosbot2.platform.discord;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.model.OutContentType;
import chiloven.lukosbot2.platform.Sender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
                // 1) Attachment with bytes -> upload as file
                if (a.bytes() != null) {
                    String name = (a.name() == null || a.name().isBlank())
                            ? (a.type() == OutContentType.IMAGE ? "image.bin" : "file.bin")
                            : a.name();
                    uploads.add(FileUpload.fromData(new ByteArrayInputStream(a.bytes()), name));
                } else if (a.type() == OutContentType.IMAGE && a.url() != null && !a.url().isBlank()) {
                    // 2) Image URL -> embed image
                    embeds.add(new EmbedBuilder().setImage(a.url()).build());
                }
            }
        }

        String content = buildContent(out, false);

        if (out.addr().group()) {
            TextChannel ch = stack.jda.getTextChannelById(out.addr().chatId());
            if (ch == null) return;

            sendToChannel(ch, uploads, embeds, content);

        } else {
            long userId = out.addr().chatId();
            stack.jda.retrieveUserById(userId)
                    .flatMap(User::openPrivateChannel)
                    .flatMap(pc -> {
                        sendToChannel(pc, uploads, embeds, content);
                        return pc.sendMessage(""); // dummy return, actual send done inside
                    }).queue();
        }
    }

    private void sendToChannel(MessageChannel ch,
                               List<FileUpload> uploads,
                               List<MessageEmbed> embeds,
                               String content) {

        final int MAX_CONTENT = 2000;
        final int MAX_EMBED_DESC = 4096;

        if (content == null) content = "";

        if (content.length() <= MAX_CONTENT) {
            // Normal send with content
            if (uploads.isEmpty()) {
                ch.sendMessage(content).setEmbeds(embeds).queue();
            } else {
                ch.sendFiles(uploads).setContent(content).setEmbeds(embeds).queue();
            }
            return;
        }

        // Content too long -> put text into one/multiple embeds
        List<MessageEmbed> mergedEmbeds = new ArrayList<>(embeds);

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + MAX_EMBED_DESC);
            String part = content.substring(start, end);

            EmbedBuilder eb = new EmbedBuilder().setDescription(part);

            mergedEmbeds.add(eb.build());
            start = end;
        }

        // Send without content (avoid 2000 limit), only embeds + optional files
        if (uploads.isEmpty()) {
            ch.sendMessage("").setEmbeds(mergedEmbeds).queue();
        } else {
            ch.sendFiles(uploads).setContent("").setEmbeds(mergedEmbeds).queue();
        }
    }
}
