package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.ScopeType;
import top.chiloven.lukosbot2.core.state.StateRegistry;
import top.chiloven.lukosbot2.core.state.StateService;
import top.chiloven.lukosbot2.core.state.definition.StateDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

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

    public PrefCommand(StateRegistry registry, StateService states) {
        this.registry = registry;
        this.states = states;
    }

    @Override
    public String name() {
        return "pref";
    }

    @Override
    public String description() {
        return "配置/记忆状态（例如语言、状态等）";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("显示可用的 StateDefinition 列表")
                .syntax("读取当前生效值", UsageNode.arg("state"))
                .syntax("设置值", UsageNode.arg("state"), UsageNode.arg("value"))
                .param("state", "StateDefinition 名称（如 lang/status）")
                .param("value", "值（字符串；不同 state 有不同约束）")
                .example(
                        "pref",
                        "pref lang",
                        "pref lang zh-cn",
                        "pref status busy"
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
                        .then(argument("state", StringArgumentType.word())
                                .executes(ctx -> {
                                    get(ctx.getSource(), getString(ctx, "state"));
                                    return 1;
                                })
                                .then(argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            set(ctx.getSource(), getString(ctx, "state"), getString(ctx, "value"));
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private String renderList() {
        String defs = registry.all().stream()
                .sorted(Comparator.comparing(StateDefinition::name))
                .map(d -> {
                    String line = "- %s (%s)".formatted(d.name(), d.description());
                    List<String> sv = d.suggestValues();
                    if (sv != null && !sv.isEmpty()) {
                        line += "  可选值: " + String.join(", ", sv);
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));

        return "可用的配置：\n" + (defs.isBlank() ? "(none)" : defs);
    }

    private void get(CommandSource src, String stateName) {
        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未知的 state：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        StateDefinition<Object> def = (StateDefinition<Object>) opt.get();
        Object v = states.resolve(def, src.addr(), src.userId());
        src.reply("%s = %s".formatted(def.name(), def.format(v)));
    }

    private void set(CommandSource src, String stateName, String rawValue) {
        log.info("Setting state {} to {} for user {} in chat {}.", stateName, rawValue, src.userId(), src.chatId());

        var opt = registry.find(stateName);
        if (opt.isEmpty()) {
            src.reply("未知的 state：" + stateName + "\n\n" + renderList());
            return;
        }

        @SuppressWarnings("unchecked")
        StateDefinition<Object> def = (StateDefinition<Object>) opt.get();

        try {
            Scope scope = states.preferredScope(def, src.addr(), src.userId());
            states.set(def, src.addr(), src.userId(), rawValue);

            Object v = states.resolve(def, src.addr(), src.userId());
            String scopeHint = switch (scope.type()) {
                case USER -> "USER";
                case CHAT -> "CHAT";
                case GLOBAL -> "GLOBAL";
            };

            // If the userId is missing but preferred scope is USER, make it obvious
            if (def.preferredScope() == ScopeType.USER && src.userId() == null) {
                scopeHint += "(no userId -> fallback)";
            }

            src.reply("已设置 %s = %s （scope=%s）".formatted(def.name(), def.format(v), scopeHint));
        } catch (IllegalArgumentException e) {
            src.reply("设置失败：" + e.getMessage());
        } catch (Exception e) {
            src.reply("设置失败：" + e.getClass().getSimpleName());
        }
    }

}
