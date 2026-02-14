package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;

@Service
public class StartCommand implements IBotCommand {
    @Override
    public String name() {
        return "start";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("（内部命令/隐藏命令）")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply("欢迎使用 LukosBot2！这是由 @chiloven945 制作的聊天机器人，你可以在 Discord 和 Telegram 上找到他！请输入 /help 以查看可用命令。");
                            return 1;
                        })
        );
    }

    @Override
    public boolean isVisible() {
        return false;
    }
}
