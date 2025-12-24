package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.command.CommandSource;
import com.mojang.brigadier.CommandDispatcher;

public class CommandCommand implements BotCommand {
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
