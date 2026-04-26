package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.auth.AuthorizationService;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.ScopeType;
import top.chiloven.lukosbot2.core.state.StateRegistry;
import top.chiloven.lukosbot2.core.state.StateService;
import top.chiloven.lukosbot2.core.state.definition.IStateDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "pref",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class PrefCommand implements IBotCommand {

    private final StateRegistry registry;
    private final StateService states;
    private final AuthorizationService authz;

    public PrefCommand(
            StateRegistry registry,
            StateService states,
            AuthorizationService authz
    ) {
        this.registry = registry;
        this.states = states;
        this.authz = authz;
    }

    @Override
    public String name() {
        return "pref";
    }

    @Override
    public String description() {
        return "查看和管理偏好设置";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("列出所有可用配置项")
                .syntax("查看当前生效的配置值", UsageNode.arg("get"), UsageNode.arg("state"))
                .syntax("查看指定范围内的配置值", UsageNode.arg("get"), UsageNode.arg("scope"), UsageNode.arg("state"))
                .syntax("设置指定范围内的配置值", UsageNode.arg("set"), UsageNode.arg("scope"), UsageNode.arg("state"), UsageNode.arg("value"))
                .syntax("清除指定范围内的配置值", UsageNode.arg("clear"), UsageNode.arg("scope"), UsageNode.arg("state"))
                .subcommand("list", "列出配置项", b -> b.syntax("列出配置项"))
                .param("scope", "配置范围：user（本人）/ chat（当前聊天）/ global（全局）")
                .param("state", "配置项名称（见 /pref list）")
                .param("value", "要设置的值")
                .note("user 代表本人配置；chat 代表当前聊天配置，需要当前聊天管理员或机器人管理员权限；global 代表全局配置，仅机器人管理员可修改。")
                .example(
                        "pref list",
                        "pref get lang",
                        "pref get chat lang",
                        "pref set user lang en-us",
                        "pref set chat quietMode true",
                        "pref clear global notifyMode"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
                            ctx.getSource().reply(renderList());
                            return 1;
                        })
                        .then(literal("list")
                                .executes(ctx -> {
                                    ctx.getSource().reply(renderList());
                                    return 1;
                                })
                        )
                        .then(literal("get")
                                .then(argument("state", StringArgumentType.word())
                                        .executes(ctx -> {
                                            getResolved(ctx.getSource(), getString(ctx, "state"));
                                            return 1;
                                        })
                                )
                                .then(argument("scope", StringArgumentType.word())
                                        .then(argument("state", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    getExplicit(ctx.getSource(), getString(ctx, "scope"), getString(ctx, "state"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("set")
                                .then(argument("scope", StringArgumentType.word())
                                        .then(argument("state", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            setExplicit(ctx.getSource(), getString(ctx, "scope"), getString(ctx, "state"), getString(ctx, "value"));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(literal("clear")
                                .then(argument("scope", StringArgumentType.word())
                                        .then(argument("state", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    clearExplicit(ctx.getSource(), getString(ctx, "scope"), getString(ctx, "state"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private static String displayScope(ScopeType type) {
        return switch (type) {
            case USER -> "本人";
            case CHAT -> "当前聊天";
            case GLOBAL -> "全局";
        };
    }

    private String renderList() {
        String defs = registry.all().stream()
                .sorted(Comparator.comparing(IStateDefinition::name))
                .map(d -> {
                    String line = "- %s：%s；可用范围：%s；生效优先级：%s".formatted(
                            d.name(),
                            d.description(),
                            d.allowedScopes().stream()
                                    .map(PrefCommand::displayScope)
                                    .sorted()
                                    .collect(Collectors.joining("/")),
                            d.resolveOrder().stream()
                                    .map(PrefCommand::displayScope)
                                    .collect(Collectors.joining(" → "))
                    );
                    List<String> sv = d.suggestValues();
                    if (sv != null && !sv.isEmpty()) {
                        line += "；可选值：" + String.join("、", sv);
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));

        return "可用的配置项：\n" + (defs.isBlank() ? "暂无可用配置项。" : defs);
    }

    private void getResolved(CommandSource src, String stateName) {
        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未找到配置项：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        IStateDefinition<Object> def = (IStateDefinition<Object>) opt.get();
        Object v = states.resolve(def, src.addr(), src.userIdOrNull());
        src.reply("%s 当前生效值：%s".formatted(def.name(), def.format(v)));
    }

    private void getExplicit(CommandSource src, String scopeRaw, String stateName) {
        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未找到配置项：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        IStateDefinition<Object> def = (IStateDefinition<Object>) opt.get();

        try {
            Scope scope = parseScope(scopeRaw, src, def);
            Object v = states.getAtScope(def, scope);
            src.reply(v == null
                    ? "%s 在%s范围内未单独设置。".formatted(def.name(), displayScope(scope.type()))
                    : "%s 在%s范围内的值：%s".formatted(def.name(), displayScope(scope.type()), def.format(v)));
        } catch (IllegalArgumentException e) {
            src.reply("读取失败：" + e.getMessage());
        }
    }

    private void setExplicit(CommandSource src, String scopeRaw, String stateName, String rawValue) {
        log.info("Setting state {} to {} for user {} in chat {}.", stateName, rawValue, src.userId(), src.chatId());

        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未找到配置项：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        IStateDefinition<Object> def = (IStateDefinition<Object>) opt.get();

        try {
            Scope scope = parseScope(scopeRaw, src, def);
            if (!ensureWriteAllowed(src, scope.type(), def.name())) {
                return;
            }

            states.setAtScope(def, scope, rawValue);
            Object v = states.getAtScope(def, scope);
            src.reply("已将 %s 在%s范围内设置为：%s".formatted(def.name(), displayScope(scope.type()), def.format(v)));
        } catch (IllegalArgumentException e) {
            src.reply("设置失败：" + e.getMessage());
        } catch (Exception e) {
            src.reply("设置失败：请检查输入值是否符合此配置项要求。");
        }
    }

    private void clearExplicit(CommandSource src, String scopeRaw, String stateName) {
        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未找到配置项：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        IStateDefinition<Object> def = (IStateDefinition<Object>) opt.get();

        try {
            Scope scope = parseScope(scopeRaw, src, def);
            if (!ensureWriteAllowed(src, scope.type(), def.name())) {
                return;
            }

            states.clearAtScope(def, scope);
            src.reply("已清除 %s 在%s范围内的设置。".formatted(def.name(), displayScope(scope.type())));
        } catch (IllegalArgumentException e) {
            src.reply("清除失败：" + e.getMessage());
        }
    }

    private Scope parseScope(String raw, CommandSource src, IStateDefinition<?> def) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("配置范围不能为空，可选值：user、chat、global。");
        }

        ScopeType type = switch (raw.trim().toLowerCase()) {
            case "user" -> ScopeType.USER;
            case "chat" -> ScopeType.CHAT;
            case "global" -> ScopeType.GLOBAL;
            default -> throw new IllegalArgumentException("未知配置范围：" + raw + "。可选值：user、chat、global。");
        };

        if (!def.allowedScopes().contains(type)) {
            throw new IllegalArgumentException("配置项“" + def.name() + "”不支持“" + displayScope(type) + "”范围。");
        }

        return switch (type) {
            case USER -> {
                Long userId = src.userIdOrNull();
                if (userId == null) throw new IllegalArgumentException("当前平台没有提供用户 ID，无法使用 user 范围。");
                yield Scope.user(src.platform(), userId);
            }
            case CHAT -> Scope.chat(src.addr());
            case GLOBAL -> Scope.global();
        };
    }

    private boolean ensureWriteAllowed(CommandSource src, ScopeType type, String stateName) {
        return switch (type) {
            case USER -> true;
            case CHAT -> authz.ensureChatManager(src, "修改当前聊天配置（" + stateName + "）");
            case GLOBAL -> authz.ensureBotAdmin(src, "修改全局配置（" + stateName + "）");
        };
    }

}
