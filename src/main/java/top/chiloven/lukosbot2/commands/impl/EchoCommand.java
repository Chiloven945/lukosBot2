package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
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
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("回显输入的文本", UsageNode.arg("text"))
                .param("text", "要回显的文本（支持空格）")
                .example("echo Hello world")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usageText());
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
