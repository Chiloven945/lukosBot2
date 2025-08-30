package chiloven.lukosbot2.core;

import chiloven.lukosbot2.commands.BotCommand;
import com.mojang.brigadier.CommandDispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandRegistry {
    private final List<BotCommand> list = new ArrayList<>();

    public CommandRegistry add(BotCommand c) {
        list.add(c);
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
}
