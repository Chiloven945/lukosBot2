package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;

/**
 * Bot command interface
 */
public interface BotCommand {
    /**
     * Main command name (used in registration and help list)
     */
    String name();

    /**
     * Brief description of the command (used in help list)
     */
    String description();

    /**
     * Register itself to the dispatcher (including subcommands and arguments)
     */
    void register(CommandDispatcher<CommandSource> dispatcher);
}
