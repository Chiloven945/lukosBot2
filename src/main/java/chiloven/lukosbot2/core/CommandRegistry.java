package chiloven.lukosbot2.core;

import chiloven.lukosbot2.commands.BotCommand;
import com.mojang.brigadier.CommandDispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandRegistry {
    private final List<BotCommand> list = new ArrayList<>();

    /**
     * Add a command to the registry
     *
     * @param c the command to add
     * @return this registry for chaining
     */
    public CommandRegistry add(BotCommand c) {
        list.add(c);
        return this;
    }

    /**
     * Add multiple commands to the registry
     *
     * @param c the commands to add
     * @return this registry for chaining
     */
    public CommandRegistry add(BotCommand... c){
        Collections.addAll(list, c);
        return this;
    }

    public List<BotCommand> all() {
        return Collections.unmodifiableList(list);
    }

    public void registerAll(CommandDispatcher<CommandSource> dispatcher) {
        for (BotCommand c : list) c.register(dispatcher);
    }

    public String listCommands() {
        StringBuilder sb = new StringBuilder();
        for (BotCommand c : list) {
            sb.append(c.name());
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Get a command by its name (case-insensitive).
     *
     * @param name the command name
     * @return the BotCommand instance, or null if not found
     */
    public BotCommand get(String name) {
        if (name == null) return null;
        for (BotCommand c : list) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
