package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;

public class CommandCommand implements IBotCommand {
    @Override
    public String name() {
        return "command";
    }

    @Override
    public String description() {
        return "管理机器人的命令";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("WIP")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }
}
