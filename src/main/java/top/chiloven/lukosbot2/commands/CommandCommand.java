package top.chiloven.lukosbot2.commands;

import com.mojang.brigadier.CommandDispatcher;
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
    public String usage() {
        return "";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }
}
