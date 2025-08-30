package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class PingCommand implements BotCommand {
    @Override
    public String name() {
        return "ping";
    }

    @Override
    public String description() {
        return "健康检查：回复 pong";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply("pong");
                            return 1;
                        })
        );
    }
}
