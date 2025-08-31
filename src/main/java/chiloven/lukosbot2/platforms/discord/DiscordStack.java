package chiloven.lukosbot2.platforms.discord;

import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.platforms.ChatPlatform;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;
import java.util.function.Consumer;

final class DiscordStack implements AutoCloseable {
    private final String token;
    JDA jda;
    private Consumer<MessageIn> sink = __ -> {
    };

    DiscordStack(String token) {
        this.token = token;
    }

    void setSink(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
    }

    /**
     * Start the receiver (idempotent)
     *
     * @throws Exception on failure
     */
    void ensureStarted() throws Exception {
        if (jda != null) return;
        jda = JDABuilder.createDefault(token,
                        EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.DIRECT_MESSAGES))
                .addEventListeners(new Listener())
                .build()
                .awaitReady();
    }

    @Override
    public void close() {
        if (jda != null) jda.shutdown();
    }

    /**
     * Send universally: group=true is treated as Guild text channel; false is treated as private chat (userId)
     *
     * @param out message to send
     */
    void send(MessageOut out) {
        if (out.addr().group()) {
            TextChannel ch = jda.getTextChannelById(out.addr().chatId());
            if (ch != null) ch.sendMessage(out.text()).queue();
        } else {
            long userId = out.addr().chatId(); // 私聊时 chatId 复用为对方 userId
            jda.retrieveUserById(userId)
                    .flatMap(User::openPrivateChannel)
                    .flatMap(pc -> pc.sendMessage(out.text()))
                    .queue();
        }
    }

    private final class Listener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent e) {
            // 忽略 Bot 本身
            if (e.getAuthor().isBot()) return;
            String text = e.getMessage().getContentRaw();
            if (text.isBlank()) return;

            boolean isGuild = e.isFromGuild();
            long chatId = isGuild ? e.getChannel().getIdLong()
                    : e.getAuthor().getIdLong(); // 私聊：用对方 userId 当 chatId
            Long userId = e.getAuthor().getIdLong();

            Address addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);
            sink.accept(new MessageIn(addr, userId, text));
        }
    }
}
