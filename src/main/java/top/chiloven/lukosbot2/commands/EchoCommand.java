package top.chiloven.lukosbot2.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.command.CommandSource;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "echo",
        havingValue = "true",
        matchIfMissing = true
)
public class EchoCommand implements IBotCommand {
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
