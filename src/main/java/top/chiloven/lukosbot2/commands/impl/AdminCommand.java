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
        return "管理 bot admin 与查看当前身份";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("查看当前身份", UsageNode.arg("me"))
                .syntax("列出 bot admins", UsageNode.arg("list"))
                .syntax("新增动态 bot admin", UsageNode.arg("add"), UsageNode.arg("platform"), UsageNode.arg("userId"))
                .syntax("移除动态 bot admin", UsageNode.arg("remove"), UsageNode.arg("platform"), UsageNode.arg("userId"))
                .param("platform", "telegram | discord | onebot")
                .param("userId", "平台稳定 user id")
                .note("list/add/remove 仅允许 bot admin 执行。bootstrap admins 由配置文件控制，remove 不会删掉配置中的管理员。")
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
                platform = %s
                userId = %s
                chatId = %s
                group = %s
                botAdmin = %s
                chatAdmin = %s
                """.formatted(
                src.platform().name(),
                src.userIdOrNull(),
                src.chatId(),
                src.isGroup(),
                auth.botAdmin(),
                auth.chatAdmin()
        ).trim());
    }

    private void list(CommandSource src) {
        if (!authz.ensureBotAdmin(src, "查看 bot admin 列表")) {
            return;
        }

        Map<ChatPlatform, Set<Long>> admins = botAdmins.listEffectiveAdmins();
        String text = admins.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .map(e -> "- %s: %s".formatted(
                        e.getKey().name(),
                        e.getValue().isEmpty() ? "(none)" : e.getValue().stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))
                ))
                .collect(Collectors.joining("\n", "有效 bot admins：\n", ""));
        src.reply(text);
    }

    private void add(CommandSource src, String platformRaw, long userId) {
        if (!authz.ensureBotAdmin(src, "新增 bot admin")) {
            return;
        }

        ChatPlatform platform = parsePlatform(platformRaw, src);
        if (platform == null) return;

        botAdmins.addDynamicAdmin(platform, userId);
        src.reply("已新增动态 bot admin：%s:%d".formatted(platform.name(), userId));
    }

    private void remove(CommandSource src, String platformRaw, long userId) {
        if (!authz.ensureBotAdmin(src, "移除 bot admin")) {
            return;
        }

        ChatPlatform platform = parsePlatform(platformRaw, src);
        if (platform == null) return;

        botAdmins.removeDynamicAdmin(platform, userId);
        src.reply("已移除动态 bot admin：%s:%d（若该用户仍在 bootstrap 配置中，仍会保留权限）".formatted(platform.name(), userId));
    }

    private ChatPlatform parsePlatform(String raw, CommandSource src) {
        try {
            return ChatPlatform.fromString(raw);
        } catch (IllegalArgumentException e) {
            src.reply("未知平台：" + raw + "，可选值：telegram/discord/onebot");
            return null;
        }
    }

}
