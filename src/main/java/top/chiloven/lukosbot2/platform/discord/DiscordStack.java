package top.chiloven.lukosbot2.platform.discord;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.OkHttpClient;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.command.CommandRegistry;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.inbound.*;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.util.spring.SpringBeans;

import java.util.*;
import java.util.function.Consumer;

@Log4j2
final class DiscordStack implements AutoCloseable {

    private final String token;
    private final ProxyConfigProp proxyConfigProp;

    JDA jda;
    private Consumer<InboundMessage> sink = _ -> {
    };

    DiscordStack(String token, ProxyConfigProp proxyConfigProp) {
        this.token = token;
        this.proxyConfigProp = proxyConfigProp;
    }

    void setSink(Consumer<InboundMessage> sink) {
        this.sink = (sink != null) ? sink : _ -> {
        };
    }

    void ensureStarted() throws Exception {
        if (jda != null) return;

        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        );

        JDABuilder builder = JDABuilder.createLight(token, intents)
                .addEventListeners(new Listener());

        OkHttpClient.Builder http = new OkHttpClient.Builder();
        if (proxyConfigProp != null) proxyConfigProp.applyTo(http);
        builder.setHttpClientBuilder(http);

        jda = builder.build().awaitReady();

        try {
            CommandRegistry registry = SpringBeans.getBean(CommandRegistry.class);

            List<SlashCommandData> slashCommands = registry.all().stream()
                    .filter(IBotCommand::isVisible)
                    .map(cmd -> {
                        String name = cmd.name();
                        if (name == null) {
                            return null;
                        }
                        String slashName = name.toLowerCase();
                        if (!slashName.matches("^[a-z0-9_-]{1,32}$")) {
                            return null;
                        }
                        String desc = cmd.description();
                        if (desc == null || desc.isBlank()) {
                            desc = "No description provided.";
                        }
                        return Commands.slash(slashName, desc);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (!slashCommands.isEmpty()) {
                var action = jda.updateCommands();
                slashCommands.forEach(action::addCommands);
                action.queue();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public void close() {
        if (jda != null) jda.shutdown();
    }

    private final class Listener extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
            if (e.getUser().isBot()) return;

            boolean isGuild = e.isFromGuild();
            long chatId = isGuild ? e.getChannel().getIdLong() : e.getUser().getIdLong();
            long userId = e.getUser().getIdLong();

            StringBuilder sb = new StringBuilder();
            sb.append('/').append(e.getName());
            e.getOptions().forEach(opt -> sb.append(' ').append(opt.getAsString()));

            String text = sb.toString();

            Address addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);

            Sender sender = new Sender(userId, e.getUser().getName(), e.getUser().getName(), e.getUser().isBot());
            Chat chat = new Chat(addr, null);
            MessageMeta meta = new MessageMeta(String.valueOf(e.getIdLong()), System.currentTimeMillis(), null, "slash");

            List<InPart> parts = new ArrayList<>();
            parts.add(new InText(text));

            sink.accept(new InboundMessage(addr, sender, chat, meta, parts, Map.of("slash", true)));

            e.reply("（推荐直接发送消息）").queue();
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent e) {
            if (e.getAuthor().isBot()) return;

            boolean isGuild = e.isFromGuild();
            long chatId = isGuild ? e.getChannel().getIdLong() : e.getAuthor().getIdLong();
            long userId = e.getAuthor().getIdLong();

            Address addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);

            String display = (e.getMember() != null) ? e.getMember().getEffectiveName() : e.getAuthor().getName();
            Sender sender = new Sender(userId, e.getAuthor().getName(), display, e.getAuthor().isBot());
            Chat chat = new Chat(addr, null);

            String msgId = String.valueOf(e.getMessageIdLong());
            Long ts = null;
            try {
                ts = e.getMessage().getTimeCreated().toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
            MessageMeta meta = new MessageMeta(msgId, ts, null, null);

            List<InPart> parts = new ArrayList<>();

            String text = e.getMessage().getContentRaw();
            if (!text.isBlank()) {
                parts.add(new InText(text));
            }

            // attachments
            var atts = e.getMessage().getAttachments();
            for (var a : atts) {
                if (a == null) continue;
                String url = a.getUrl();
                String name = a.getFileName();
                String mime = a.getContentType();
                Long size = (long) a.getSize();
                if (a.isImage()) {
                    if (!url.isBlank()) {
                        parts.add(new InImage(new UrlRef(url), null, name, mime));
                    }
                } else {
                    if (!url.isBlank()) {
                        parts.add(new InFile(new UrlRef(url), name, mime, size, null));
                    }
                }
            }

            // ignore empty messages
            if (parts.isEmpty()) return;

            sink.accept(new InboundMessage(addr, sender, chat, meta, parts, Map.of()));
        }

    }

}
