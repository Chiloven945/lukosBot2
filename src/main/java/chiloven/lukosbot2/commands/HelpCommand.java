package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class HelpCommand implements BotCommand {
    private final CommandRegistry registry;
    private final String prefix;

    public HelpCommand(CommandRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "帮助：列出命令或显示某命令详细用法";
    }

    @Override
    public String usage() {
        return """
                用法：
                /help
                /help <command>
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        // /help
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder("可用命令：\n");
                            registry.all().forEach(c ->
                                    sb.append(prefix)
                                            .append(c.name())
                                            .append(" - ")
                                            .append(c.description())
                                            .append("\n")
                            );
                            ctx.getSource().reply(sb.toString().trim());
                            return 1;
                        })
                        // /help <command>
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.word())
                                .executes(ctx -> {
                                    String cmdName = StringArgumentType.getString(ctx, "command");
                                    BotCommand cmd = registry.get(cmdName);
                                    if (cmd != null) {
                                        ctx.getSource().reply(cmd.usage().trim());
                                    } else {
                                        ctx.getSource().reply("未知命令: " + cmdName);
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
