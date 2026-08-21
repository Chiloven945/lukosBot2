/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.platform.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry;
import top.chiloven.lukosbot2.core.model.message.Address;
import top.chiloven.lukosbot2.core.model.message.inbound.*;
import top.chiloven.lukosbot2.core.model.message.media.UrlRef;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.util.OkHttpUtils;

import java.util.*;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;

@Log4j2
final class DiscordStack implements AutoCloseable {

    private final String token;
    private final ProxyConfigProp proxyConfigProp;
    private final CommandRegistry commandRegistry;

    JDA jda;
    private Consumer<InboundMessage> sink = _ -> {
    };

    DiscordStack(
            String token,
            ProxyConfigProp proxyConfigProp,
            CommandRegistry commandRegistry
    ) {
        this.token = token;
        this.proxyConfigProp = proxyConfigProp;
        this.commandRegistry = commandRegistry;
    }

    void setSink(Consumer<InboundMessage> sink) {
        this.sink = (sink != null)
                ? sink
                : _ -> {
                };
    }

    void ensureStarted() throws Exception {
        if (jda != null) {
            return;
        }

        var intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        );

        var builder = JDABuilder
                .createLight(token, intents)
                .addEventListeners(new Listener());
        builder.setHttpClientBuilder(OkHttpUtils.newBuilder(proxyConfigProp));
        jda = builder.build().awaitReady();

        if (commandRegistry != null) {
            try {
                var slashCommands = commandRegistry.all().stream()
                        .filter(IBotCommand::isVisible)
                        .map(cmd -> {
                            var name = cmd.name();
                            if (name == null) {
                                return null;
                            }

                            var slashName = name.toLowerCase();
                            if (!slashName.matches("^[a-z0-9_-]{1,32}$")) {
                                return null;
                            }

                            var desc = cmd.description();
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
    }

    @Override
    public void close() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    private final class Listener extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
            if (e.getUser().isBot()) {
                return;
            }

            var isGuild = e.isFromGuild();
            var chatId = isGuild
                    ? e.getChannel().getIdLong()
                    : e.getUser().getIdLong();
            var userId = e.getUser().getIdLong();

            var sb = new StringBuilder();
            sb.append('/').append(e.getName());
            e.getOptions().forEach(opt -> sb.append(' ').append(opt.getAsString()));

            var text = sb.toString();

            var addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);

            var sender = new Sender(
                    userId,
                    e.getUser().getName(),
                    e.getUser().getName(),
                    e.getUser().isBot()
            );
            var chat = new Chat(addr, null);
            var meta = new MessageMeta(
                    String.valueOf(e.getIdLong()),
                    System.currentTimeMillis(),
                    null,
                    "slash"
            );

            List<InPart> parts = new ArrayList<>();
            parts.add(new InText(text));

            sink.accept(new InboundMessage(
                    addr,
                    sender,
                    chat,
                    meta,
                    parts,
                    buildExtForSlash(e),
                    null
            ));

            e.reply("（推荐直接发送消息）").queue();
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent e) {
            if (e.getAuthor().isBot()) {
                return;
            }

            var isGuild = e.isFromGuild();
            var chatId = isGuild
                    ? e.getChannel().getIdLong()
                    : e.getAuthor().getIdLong();
            var userId = e.getAuthor().getIdLong();

            var addr = new Address(ChatPlatform.DISCORD, chatId, isGuild);

            var display = (e.getMember() != null)
                    ? e.getMember().getEffectiveName()
                    : e.getAuthor().getName();
            var sender = new Sender(
                    userId,
                    e.getAuthor().getName(),
                    display,
                    e.getAuthor().isBot()
            );
            var chat = new Chat(addr, null);

            var msgId = String.valueOf(e.getMessageIdLong());
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

            var meta = new MessageMeta(msgId, ts, replyToId, null);

            var parts = extractParts(e.getMessage());
            if (parts.isEmpty()) {
                return;
            }

            var quoted = resolveQuoted(e);
            sink.accept(new InboundMessage(
                    addr,
                    sender,
                    chat,
                    meta,
                    parts,
                    buildExtForMessage(e),
                    quoted
            ));
        }

        private List<InPart> extractParts(Message message) {
            List<InPart> parts = new ArrayList<>();
            if (message == null) {
                return parts;
            }

            var text = message.getContentRaw();
            if (!text.isBlank()) {
                parts.add(new InText(text));
            }

            var atts = message.getAttachments();
            atts.stream()
                    .filter(Objects::nonNull)
                    .forEachOrdered(a -> {
                        var url = a.getUrl();
                        var name = a.getFileName();
                        var mime = a.getContentType();

                        var size = (long) a.getSize();
                        if (a.isImage()) {
                            if (!url.isBlank()) {
                                parts.add(new InImage(
                                        new UrlRef(url),
                                        null,
                                        name,
                                        mime
                                ));
                            }
                        } else if (!url.isBlank()) {
                            parts.add(new InFile(
                                    new UrlRef(url),
                                    name,
                                    mime,
                                    size,
                                    null
                            ));
                        }
                    });
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
                        var id = e.getMessage().getMessageReference().getMessageId();
                        if (!id.isBlank()) {
                            referenced = e.getChannel().retrieveMessageById(id).complete();
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Failed to resolve quoted Discord message: {}", ex.getMessage());
                }
            }

            if (referenced == null) {
                return null;
            }

            Long senderId = null;
            try {
                senderId = referenced.getAuthor().getIdLong();
            } catch (Exception _) {
            }

            return new QuotedMessage(
                    referenced.getId(),
                    senderId,
                    extractParts(referenced)
            );
        }

        private Map<String, Object> buildExtForMessage(MessageReceivedEvent e) {
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("policy.privateChat", !e.isFromGuild());
            ext.put("policy.nsfw", isNsfwChannel(e.getChannel()));
            if (!e.isFromGuild()) {
                return ext;
            }

            var guildAdmin = false;
            var chatAdmin = false;
            if (e.getMember() != null) {
                guildAdmin = e.getMember().hasPermission(Permission.ADMINISTRATOR)
                        || e.getMember().hasPermission(Permission.MANAGE_SERVER);
                chatAdmin = guildAdmin || e.getMember()
                        .hasPermission(e.getGuildChannel(), Permission.MANAGE_CHANNEL);
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

            var guildAdmin = false;
            var chatAdmin = false;
            if (e.getMember() != null) {
                guildAdmin = e.getMember().hasPermission(Permission.ADMINISTRATOR)
                        || e.getMember().hasPermission(Permission.MANAGE_SERVER);
                chatAdmin = guildAdmin || e.getMember()
                        .hasPermission(e.getGuildChannel(), Permission.MANAGE_CHANNEL);
            }

            ext.put("discord.guildId", e.getGuild().getIdLong());
            ext.put("discord.channelId", e.getChannel().getIdLong());
            ext.put("discord.guildAdmin", guildAdmin);
            ext.put("discord.chatAdmin", chatAdmin);
            return ext;
        }

        private boolean isNsfwChannel(Object channel) {
            if (channel == null) {
                return false;
            }

            try {
                var method = channel.getClass().getMethod("isNSFW");
                var value = method.invoke(channel);
                if (value instanceof Boolean b) {
                    return b;
                }
            } catch (ReflectiveOperationException _) {
            }

            try {
                var method = channel.getClass().getMethod("isNsfw");
                var value = method.invoke(channel);
                if (value instanceof Boolean b) {
                    return b;
                }
            } catch (ReflectiveOperationException _) {
            }

            return false;
        }

    }

}
