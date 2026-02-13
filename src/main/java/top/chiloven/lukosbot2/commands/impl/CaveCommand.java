package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.core.command.CommandSource;

/**
 * Record and send message.
 *
 * @author Chiloven945
 */
public class CaveCommand implements IBotCommand {
    @Override
    public String name() {
        return "";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }
}
