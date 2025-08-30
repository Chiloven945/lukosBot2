package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class EchoCommand implements BotCommand {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "回声：原样返回文本";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("text", greedyString())
                                .executes(ctx -> {
                                    String text = StringArgumentType.getString(ctx, "text");
                                    ctx.getSource().reply(text);
                                    return 1;
                                })
                        )
        );
    }
}
