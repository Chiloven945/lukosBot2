package top.chiloven.lukosbot2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;

import java.util.Collections;
import java.util.List;

@Service
public class CommandRegistry {
    private final List<IBotCommand> commands;

    public CommandRegistry(List<IBotCommand> commands) {
        this.commands = commands;
    }

    public List<IBotCommand> all() {
        return Collections.unmodifiableList(commands);
    }

    public void registerAll(CommandDispatcher<CommandSource> dispatcher) {
        for (IBotCommand c : commands) {
            c.register(dispatcher);
        }
    }

    public String listCommands() {
        StringBuilder sb = new StringBuilder();
        for (IBotCommand c : commands) {
            sb.append(c.name()).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Get a command by its name (case-insensitive).
     *
     * @param name the command name
     * @return the BotCommand instance, or null if not found
     */
    public IBotCommand get(String name) {
        if (name == null) return null;
        for (IBotCommand c : commands) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
