package top.chiloven.lukosbot2.platform.discord;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
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

            sink.accept(new InboundMessage(addr, sender, chat, meta, parts, buildExtForSlash(e), null));

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
            } catch (Exception _) {
            }
            String replyToId = null;
            try {
                if (e.getMessage().getMessageReference() != null) {
                    replyToId = e.getMessage().getMessageReference().getMessageId();
                }
            } catch (Exception _) {
            }
            MessageMeta meta = new MessageMeta(msgId, ts, replyToId, null);

            List<InPart> parts = extractParts(e.getMessage());
            if (parts.isEmpty()) return;

            QuotedMessage quoted = resolveQuoted(e);
            sink.accept(new InboundMessage(addr, sender, chat, meta, parts, buildExtForMessage(e), quoted));
        }

        private List<InPart> extractParts(Message message) {
            List<InPart> parts = new ArrayList<>();
            if (message == null) return parts;

            String text = message.getContentRaw();
            if (!text.isBlank()) parts.add(new InText(text));

            var atts = message.getAttachments();
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
                } else if (!url.isBlank()) {
                    parts.add(new InFile(new UrlRef(url), name, mime, size, null));
                }
            }
            return parts;
        }

        private QuotedMessage resolveQuoted(MessageReceivedEvent e) {
            Message referenced = null;
            try {
                referenced = e.getMessage().getReferencedMessage();
            } catch (Exception _) {
            }
            if (referenced == null) {
                try {
                    if (e.getMessage().getMessageReference() != null) {
                        String id = e.getMessage().getMessageReference().getMessageId();
                        if (!id.isBlank()) {
                            referenced = e.getChannel().retrieveMessageById(id).complete();
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Failed to resolve quoted Discord message: {}", ex.getMessage());
                }
            }
            if (referenced == null) return null;

            Long senderId = null;
            try {
                senderId = referenced.getAuthor().getIdLong();
            } catch (Exception _) {
            }
            return new QuotedMessage(referenced.getId(), senderId, extractParts(referenced));
        }

        private Map<String, Object> buildExtForMessage(MessageReceivedEvent e) {
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("policy.privateChat", !e.isFromGuild());
            ext.put("policy.nsfw", isNsfwChannel(e.getChannel()));
            if (!e.isFromGuild()) {
                return ext;
            }

            boolean guildAdmin = false;
            boolean chatAdmin = false;
            if (e.getMember() != null) {
                guildAdmin = e.getMember().hasPermission(Permission.ADMINISTRATOR)
                        || e.getMember().hasPermission(Permission.MANAGE_SERVER);
                chatAdmin = guildAdmin || e.getMember().hasPermission(e.getGuildChannel(), Permission.MANAGE_CHANNEL);
            }

            ext.put("discord.guildId", e.getGuild().getIdLong());
            ext.put("discord.channelId", e.getChannel().getIdLong());
            ext.put("discord.guildAdmin", guildAdmin);
            ext.put("discord.chatAdmin", chatAdmin);
            return ext;
        }

        private Map<String, Object> buildExtForSlash(SlashCommandInteractionEvent e) {
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("slash", true);
            ext.put("policy.privateChat", !e.isFromGuild());
            ext.put("policy.nsfw", isNsfwChannel(e.getChannel()));
            if (!e.isFromGuild()) {
                return ext;
            }

            boolean guildAdmin = false;
            boolean chatAdmin = false;
            if (e.getMember() != null) {
                guildAdmin = e.getMember().hasPermission(Permission.ADMINISTRATOR)
                        || e.getMember().hasPermission(Permission.MANAGE_SERVER);
                chatAdmin = guildAdmin || e.getMember().hasPermission(e.getGuildChannel(), Permission.MANAGE_CHANNEL);
            }

            ext.put("discord.guildId", e.getGuild().getIdLong());
            ext.put("discord.channelId", e.getChannel().getIdLong());
            ext.put("discord.guildAdmin", guildAdmin);
            ext.put("discord.chatAdmin", chatAdmin);
            return ext;
        }

        private boolean isNsfwChannel(Object channel) {
            if (channel == null) return false;
            try {
                var method = channel.getClass().getMethod("isNSFW");
                Object value = method.invoke(channel);
                if (value instanceof Boolean b) return b;
            } catch (ReflectiveOperationException _) {
            }
            try {
                var method = channel.getClass().getMethod("isNsfw");
                Object value = method.invoke(channel);
                if (value instanceof Boolean b) return b;
            } catch (ReflectiveOperationException _) {
            }
            return false;
        }

    }

}
