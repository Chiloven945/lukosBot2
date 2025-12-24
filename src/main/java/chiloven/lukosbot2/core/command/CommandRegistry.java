package chiloven.lukosbot2.core.command;

import chiloven.lukosbot2.commands.BotCommand;
import com.mojang.brigadier.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CommandRegistry {
    private final List<BotCommand> commands;

    public CommandRegistry(List<BotCommand> commands) {
        this.commands = commands;
    }

    public List<BotCommand> all() {
        return Collections.unmodifiableList(commands);
    }

    public void registerAll(CommandDispatcher<CommandSource> dispatcher) {
        for (BotCommand c : commands) {
            c.register(dispatcher);
        }
    }

    public String listCommands() {
        StringBuilder sb = new StringBuilder();
        for (BotCommand c : commands) {
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
    public BotCommand get(String name) {
        if (name == null) return null;
        for (BotCommand c : commands) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
