package top.chiloven.lukosbot2.core.command;

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

    public boolean contains(IBotCommand cmd) {
        return commands.contains(cmd);
    }

    /**
     * Get a command by its name (case-insensitive).
     *
     * @param name the command name
     * @return the IBotCommand instance, or null if not found
     */
    public IBotCommand get(String name) {
        if (name == null) return null;
        return commands.stream()
                .filter(c -> c.matches(name))
                .findFirst()
                .orElse(null);
    }

}
