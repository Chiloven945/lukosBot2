package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.stereotype.Service;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

@Service
public class EchoCommand implements BotCommand {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "原样返回文本";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/echo <text>` # 返回输入的文本
                示例：
                /echo Hello, world!
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
                        .then(argument("text", greedyString())
                                .executes(ctx -> {
                                    String text = StringArgumentType.getString(ctx, "text");
                                    ctx.getSource().reply(text);
                                    return 1;
                                })
                        )
        );
    }
}
