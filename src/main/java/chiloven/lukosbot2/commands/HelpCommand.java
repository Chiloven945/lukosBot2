package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class HelpCommand implements BotCommand {

    private final ObjectProvider<CommandRegistry> registryProvider;
    private final AppProperties appProperties;

    public HelpCommand(ObjectProvider<CommandRegistry> registryProvider, AppProperties appProperties) {
        this.registryProvider = registryProvider;
        this.appProperties = appProperties;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        // /help
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder("可用命令：\n");
                            registry().all().stream()
                                    .filter(BotCommand::isVisible)
                                    .forEach(c -> sb.append(appProperties.getPrefix())
                                            .append(c.name())
                                            .append(" - ")
                                            .append(c.description())
                                            .append("\n")
                                    );
                            sb.append("\n使用 `/help <command>` 查看具体命令的用法。");
                            ctx.getSource().reply(sb.toString().trim());
                            return 1;
                        })
                        // /help <command>
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.word())
                                .executes(ctx -> {
                                    String cmdName = StringArgumentType.getString(ctx, "command");
                                    var cmd = registry().get(cmdName);

                                    if (cmd != null && cmd.isVisible()) {
                                        ctx.getSource().reply(cmd.usage().trim());
                                    } else {
                                        ctx.getSource().reply(
                                                "未知的命令: %s\n使用 `/help` 查看可用命令列表。"
                                                        .formatted(cmdName)
                                        );
                                    }
                                    return 1;
                                })
                        )
        );
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "列出可用命令或其详细用法";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/help`           # 列出所有可用命令
                `/help <command>` # 显示指定命令的用法
                """;
    }

    private CommandRegistry registry() {
        return registryProvider.getObject();
    }
}
