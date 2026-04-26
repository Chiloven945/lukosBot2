package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.auth.AuthContext;
import top.chiloven.lukosbot2.core.auth.AuthorizationService;
import top.chiloven.lukosbot2.core.auth.BotAdminService;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "admin",
        havingValue = "true",
        matchIfMissing = true
)
public class AdminCommand implements IBotCommand {

    private final BotAdminService botAdmins;
    private final AuthorizationService authz;

    public AdminCommand(BotAdminService botAdmins, AuthorizationService authz) {
        this.botAdmins = botAdmins;
        this.authz = authz;
    }

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public String description() {
        return "管理机器人管理员并查看当前身份";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("查看你的当前身份", UsageNode.arg("me"))
                .syntax("查看机器人管理员列表", UsageNode.arg("list"))
                .syntax("添加机器人管理员", UsageNode.arg("add"), UsageNode.arg("platform"), UsageNode.arg("userId"))
                .syntax("移除机器人管理员", UsageNode.arg("remove"), UsageNode.arg("platform"), UsageNode.arg("userId"))
                .param("platform", "平台：telegram / discord / onebot")
                .param("userId", "平台用户 ID")
                .note("list/add/remove 仅机器人管理员可用。配置文件中的管理员不会被 remove 移除。")
                .example(
                        "admin me",
                        "admin list",
                        "admin add telegram 123456789",
                        "admin remove discord 987654321"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
                            return 1;
                        })
                        .then(literal("me")
                                .executes(ctx -> {
                                    me(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(literal("list")
                                .executes(ctx -> {
                                    list(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(literal("add")
                                .then(argument("platform", StringArgumentType.word())
                                        .then(argument("userId", LongArgumentType.longArg(1L))
                                                .executes(ctx -> {
                                                    add(ctx.getSource(), getString(ctx, "platform"), getLong(ctx, "userId"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(argument("platform", StringArgumentType.word())
                                        .then(argument("userId", LongArgumentType.longArg(1L))
                                                .executes(ctx -> {
                                                    remove(ctx.getSource(), getString(ctx, "platform"), getLong(ctx, "userId"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private void me(CommandSource src) {
        AuthContext auth = authz.inspect(src);
        src.reply("""
                平台：%s
                用户 ID：%s
                聊天 ID：%s
                群聊：%s
                机器人管理员：%s
                聊天管理员：%s
                """.formatted(
                src.platform().name(),
                src.userIdOrNull() == null ? "未知" : src.userIdOrNull(),
                src.chatId(),
                src.isGroup() ? "是" : "否",
                auth.botAdmin() ? "是" : "否",
                auth.chatAdmin() ? "是" : "否"
        ).trim());
    }

    private void list(CommandSource src) {
        if (!authz.ensureBotAdmin(src, "查看机器人管理员列表")) {
            return;
        }

        Map<ChatPlatform, Set<Long>> admins = botAdmins.listEffectiveAdmins();
        String text = admins.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .map(e -> "- %s: %s".formatted(
                        e.getKey().name(),
                        e.getValue().isEmpty() ? "无" : e.getValue().stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))
                ))
                .collect(Collectors.joining("\n", "当前有效的机器人管理员：\n", ""));
        src.reply(text);
    }

    private void add(CommandSource src, String platformRaw, long userId) {
        if (!authz.ensureBotAdmin(src, "添加机器人管理员")) {
            return;
        }

        ChatPlatform platform = parsePlatform(platformRaw, src);
        if (platform == null) return;

        botAdmins.addDynamicAdmin(platform, userId);
        src.reply("已添加机器人管理员：%s:%d".formatted(platform.name(), userId));
    }

    private void remove(CommandSource src, String platformRaw, long userId) {
        if (!authz.ensureBotAdmin(src, "移除机器人管理员")) {
            return;
        }

        ChatPlatform platform = parsePlatform(platformRaw, src);
        if (platform == null) return;

        botAdmins.removeDynamicAdmin(platform, userId);
        src.reply("已移除机器人管理员：%s:%d（如果该用户仍在配置文件的管理员列表中，权限会继续保留）".formatted(platform.name(), userId));
    }

    private ChatPlatform parsePlatform(String raw, CommandSource src) {
        try {
            return ChatPlatform.fromString(raw);
        } catch (IllegalArgumentException e) {
            src.reply("不支持的平台：" + raw + "。可选：telegram / discord / onebot。");
            return null;
        }
    }

}
