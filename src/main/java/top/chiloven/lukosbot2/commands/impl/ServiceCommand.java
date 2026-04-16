package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.auth.AuthorizationService;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.core.service.ServiceManager;
import top.chiloven.lukosbot2.core.service.ServiceState;
import top.chiloven.lukosbot2.services.IBotService;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "service",
        havingValue = "true",
        matchIfMissing = true
)
public class ServiceCommand implements IBotCommand {

    private final ServiceManager services;
    private final AuthorizationService authz;

    public ServiceCommand(ServiceManager services, AuthorizationService authz) {
        this.services = services;
        this.authz = authz;
    }

    @Override
    public String name() {
        return "service";
    }

    @Override
    public String description() {
        return "管理 Bot 服务（支持当前聊天和全局默认配置）";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("显示本命令帮助")
                .syntax("启用/禁用指定服务（在当前聊天中生效）", UsageNode.arg("service"))
                .syntax("获取服务配置（当前聊天）", UsageNode.arg("service"), UsageNode.arg("key"))
                .syntax("设置服务配置（当前聊天）", UsageNode.arg("service"), UsageNode.arg("key"), UsageNode.arg("value"))
                .subcommand("list", "列出当前聊天支持的服务与开关状态", b -> b.syntax("列出服务列表"))
                .subcommand("global", "bot admin 管理全局默认服务状态", b -> b
                        .syntax("列出全局默认服务列表", UsageNode.arg("list"))
                        .syntax("切换全局默认服务开关", UsageNode.arg("service"))
                        .syntax("读取全局默认服务配置", UsageNode.arg("service"), UsageNode.arg("key"))
                        .syntax("设置全局默认服务配置", UsageNode.arg("service"), UsageNode.arg("key"), UsageNode.arg("value"))
                )
                .param("service", "服务名（见 /service list）")
                .param("key", "配置项键")
                .param("value", "配置项值（字符串）")
                .note("聊天级修改要求当前聊天管理员或 bot admin；global 仅允许 bot admin。")
                .example(
                        "service list",
                        "service weather",
                        "service weather intervalMs",
                        "service weather intervalMs 60000",
                        "service global list",
                        "service global weather",
                        "service global weather intervalMs 60000"
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
                        .then(literal("list")
                                .executes(ctx -> {
                                    ctx.getSource().reply(renderList(ctx.getSource()));
                                    return 1;
                                })
                        )
                        .then(literal("global")
                                .executes(ctx -> {
                                    sendUsage(ctx.getSource());
                                    return 1;
                                })
                                .then(literal("list")
                                        .executes(ctx -> {
                                            listGlobal(ctx.getSource());
                                            return 1;
                                        })
                                )
                                .then(argument("service", StringArgumentType.word())
                                        .executes(ctx -> {
                                            toggleGlobal(ctx.getSource(), getString(ctx, "service"));
                                            return 1;
                                        })
                                        .then(argument("key", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String svc = getString(ctx, "service");
                                                    String key = getString(ctx, "key");
                                                    getGlobal(ctx.getSource(), svc, key);
                                                    return 1;
                                                })
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            String svc = getString(ctx, "service");
                                                            String key = getString(ctx, "key");
                                                            String value = getString(ctx, "value");
                                                            setGlobal(ctx.getSource(), svc, key, value);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(argument("service", StringArgumentType.word())
                                .executes(ctx -> {
                                    toggle(ctx.getSource(), getString(ctx, "service"));
                                    return 1;
                                })
                                .then(argument("key", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String svc = getString(ctx, "service");
                                            String key = getString(ctx, "key");
                                            get(ctx.getSource(), svc, key);
                                            return 1;
                                        })
                                        .then(argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String svc = getString(ctx, "service");
                                                    String key = getString(ctx, "key");
                                                    String value = getString(ctx, "value");
                                                    set(ctx.getSource(), svc, key, value);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private String renderList(CommandSource src) {
        Map<String, ServiceState> st = services.snapshotStates(src.addr());

        return services.getRegistry().all().stream()
                .filter(s -> services.isAllowed(s.name()))
                .sorted(Comparator.comparing(IBotService::name))
                .map(s -> {
                    ServiceState ss = st.get(s.name());
                    boolean enabled = ss != null && ss.isEnabled();
                    String desc = s.description() == null ? "" : s.description();
                    return "- %s [%s] (%s)".formatted(
                            s.name(),
                            enabled ? "ENABLED" : "DISABLED",
                            desc
                    );
                })
                .collect(Collectors.joining("\n", "服务（本聊天）：\n", ""));
    }

    private void listGlobal(CommandSource src) {
        if (!authz.ensureBotAdmin(src, "查看全局服务配置")) {
            return;
        }

        Map<String, ServiceState> st = services.snapshotDefaultStates();
        String text = services.getRegistry().all().stream()
                .filter(s -> services.isAllowed(s.name()))
                .sorted(Comparator.comparing(IBotService::name))
                .map(s -> {
                    ServiceState ss = st.get(s.name());
                    boolean enabled = ss != null && ss.isEnabled();
                    String desc = s.description() == null ? "" : s.description();
                    return "- %s [%s] (%s)".formatted(
                            s.name(),
                            enabled ? "ENABLED" : "DISABLED",
                            desc
                    );
                })
                .collect(Collectors.joining("\n", "服务（GLOBAL 默认）：\n", ""));
        src.reply(text);
    }

    private void toggle(CommandSource src, String name) {
        if (!authz.ensureChatManager(src, "修改当前聊天服务开关")) {
            return;
        }
        if (!services.isAllowed(name)) {
            src.reply("服务未被允许使用：" + name);
            return;
        }

        var opt = services.getRegistry().find(name);
        if (opt.isEmpty()) {
            src.reply("未知的服务：" + name + "\n\n" + renderList(src));
            return;
        }

        ServiceState st = services.stateOf(src.addr(), name);
        boolean nowEnabled = (st == null) || !st.isEnabled();

        services.setEnabled(src.addr(), name, nowEnabled);
        src.reply("服务“%s”在本聊天现为 %s 状态。".formatted(name, nowEnabled ? "ENABLED" : "DISABLED"));
    }

    private void get(CommandSource src, String svc, String key) {
        if (!services.isAllowed(svc)) {
            src.reply("服务未被允许使用：" + svc);
            return;
        }

        if (services.getRegistry().find(svc).isEmpty()) {
            src.reply("未知的服务：" + svc);
            return;
        }

        ServiceState st = services.stateOf(src.addr(), svc);
        if (st == null || st.getConfig() == null) {
            src.reply("服务 %s 在本聊天没有配置。".formatted(svc));
            return;
        }

        String v = st.getConfig().get(key);
        src.reply(v == null ? "(null)" : v);
    }

    private void set(CommandSource src, String svc, String key, String value) {
        if (!authz.ensureChatManager(src, "修改当前聊天服务配置")) {
            return;
        }
        if (!services.isAllowed(svc)) {
            src.reply("服务未被允许使用：" + svc);
            return;
        }

        if (services.getRegistry().find(svc).isEmpty()) {
            src.reply("未知的服务：" + svc);
            return;
        }

        services.setConfigValue(src.addr(), svc, key, value);
        src.reply("已更新（本聊天）%s.%s = %s".formatted(svc, key, value));
    }

    private void toggleGlobal(CommandSource src, String name) {
        if (!authz.ensureBotAdmin(src, "修改全局服务开关")) {
            return;
        }
        if (!services.isAllowed(name)) {
            src.reply("服务未被允许使用：" + name);
            return;
        }

        var opt = services.getRegistry().find(name);
        if (opt.isEmpty()) {
            src.reply("未知的服务：" + name);
            return;
        }

        ServiceState st = services.defaultStateOf(name);
        boolean nowEnabled = (st == null) || !st.isEnabled();
        services.setDefaultEnabled(name, nowEnabled);
        src.reply("服务“%s”的 GLOBAL 默认现为 %s 状态。".formatted(name, nowEnabled ? "ENABLED" : "DISABLED"));
    }

    private void getGlobal(CommandSource src, String svc, String key) {
        if (!authz.ensureBotAdmin(src, "查看全局服务配置")) {
            return;
        }
        if (!services.isAllowed(svc)) {
            src.reply("服务未被允许使用：" + svc);
            return;
        }
        if (services.getRegistry().find(svc).isEmpty()) {
            src.reply("未知的服务：" + svc);
            return;
        }

        ServiceState st = services.defaultStateOf(svc);
        if (st == null || st.getConfig() == null) {
            src.reply("服务 %s 没有 GLOBAL 默认配置。".formatted(svc));
            return;
        }

        String v = st.getConfig().get(key);
        src.reply(v == null ? "(null)" : v);
    }

    private void setGlobal(CommandSource src, String svc, String key, String value) {
        if (!authz.ensureBotAdmin(src, "修改全局服务配置")) {
            return;
        }
        if (!services.isAllowed(svc)) {
            src.reply("服务未被允许使用：" + svc);
            return;
        }
        if (services.getRegistry().find(svc).isEmpty()) {
            src.reply("未知的服务：" + svc);
            return;
        }

        services.setDefaultConfigValue(svc, key, value);
        src.reply("已更新（GLOBAL 默认）%s.%s = %s".formatted(svc, key, value));
    }

}
