package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;

/**
 * Bot command interface
 */
public interface BotCommand {
    /**
     * Main name of the command
     * @return Main name of the command
     */
    String name();

    /**
     * Brief description of the command
     * @return Brief description of the command
     */
    String description();

    /**
     * Detailed usage of the command
     * @return Detailed usage of the command
     */
    String usage();

    /**
     * Register this command to the dispatcher
     * @param dispatcher The command dispatcher
     */
    void register(CommandDispatcher<CommandSource> dispatcher);

    /**
     * Choose the command whether to be displayed in the help command.
     *
     * @return A boolean value represent the visibility
     */
    default boolean isVisible() {
        return true;
    }
}
