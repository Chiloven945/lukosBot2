package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

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
        return "帮助：列出命令与简述";
    }

    @Override
    public String usage() {
        return """
                用法：
                /help
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder("可用命令：\n");
                            registry.all().forEach(c -> sb.append(prefix).append(c.name()).append(" - ").append(c.description()).append("\n"));
                            ctx.getSource().reply(sb.toString().trim());
                            return 1;
                        })
        );
    }
}
