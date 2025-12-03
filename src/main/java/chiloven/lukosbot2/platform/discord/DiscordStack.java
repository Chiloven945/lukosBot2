package chiloven.lukosbot2.platform.discord;

import chiloven.lukosbot2.config.ProxyConfig;
import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.platform.ChatPlatform;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.OkHttpClient;

import java.util.EnumSet;
import java.util.function.Consumer;

final class DiscordStack implements AutoCloseable {
    private final String token;
    private final ProxyConfig proxyConfig;

    JDA jda;
    private Consumer<MessageIn> sink = __ -> {
    };

    DiscordStack(String token, ProxyConfig proxyConfig) {
        this.token = token;
        this.proxyConfig = proxyConfig;
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

        OkHttpClient.Builder http = new OkHttpClient.Builder();
        proxyConfig.applyTo(http);
        builder.setHttpClientBuilder(http);

        jda = builder.build().awaitReady();
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
        public void onMessageReceived(MessageReceivedEvent e) {
            if (e.getAuthor().isBot()) return;
            String text = e.getMessage().getContentRaw();
            if (text.isBlank()) return;

            boolean isGuild = e.isFromGuild();
            long chatId = isGuild ? e.getChannel().getIdLong() : e.getAuthor().getIdLong();
            Long userId = e.getAuthor().getIdLong();

            Address addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);
            sink.accept(new MessageIn(addr, userId, text));
        }
    }
}
