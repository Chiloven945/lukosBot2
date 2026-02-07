package top.chiloven.lukosbot2.platform.discord;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import top.chiloven.lukosbot2.model.Address;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.model.MessageOut;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.util.spring.SpringBeans;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
final class DiscordStack implements AutoCloseable {
    private final String token;
    private final ProxyConfigProp proxyConfigProp;

    JDA jda;
    private Consumer<MessageIn> sink = __ -> {
    };

    DiscordStack(String token, ProxyConfigProp proxyConfigProp) {
        this.token = token;
        this.proxyConfigProp = proxyConfigProp;
    }

    void setSink(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
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

        ProxyConfigProp proxy = SpringBeans.getBean(ProxyConfigProp.class);
        OkHttpClient.Builder http = new OkHttpClient.Builder();
        proxy.applyTo(http);
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

    void send(MessageOut out) {
        if (out.addr().group()) {
            TextChannel ch = jda.getTextChannelById(out.addr().chatId());
            if (ch != null) ch.sendMessage(out.text()).queue();
        } else {
            long userId = out.addr().chatId();
            jda.retrieveUserById(userId)
                    .flatMap(User::openPrivateChannel)
                    .flatMap(pc -> pc.sendMessage(out.text()))
                    .queue();
        }
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
            sink.accept(new MessageIn(addr, userId, text));

            e.reply("（推荐直接发送消息）").queue();
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent e) {
            if (e.getAuthor().isBot()) return;
            String text = e.getMessage().getContentRaw();
            if (text.isBlank()) return;

            boolean isGuild = e.isFromGuild();
            long chatId = isGuild ? e.getChannel().getIdLong() : e.getAuthor().getIdLong();
            long userId = e.getAuthor().getIdLong();

            Address addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);
            sink.accept(new MessageIn(addr, userId, text));
        }
    }
}
