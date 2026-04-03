package top.chiloven.lukosbot2.core.cli;

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.cli.ICliCommand;

import java.util.Collections;
import java.util.List;

@Service
public class CliCmdRegistry {

    private final List<ICliCommand> cliCommands;

    public CliCmdRegistry(List<ICliCommand> iCliCommands) {
        this.cliCommands = iCliCommands;
    }

    public List<ICliCommand> all() {
        return Collections.unmodifiableList(cliCommands);
    }

    public boolean contains(ICliCommand cmd) {
        return cliCommands.contains(cmd);
    }

    /**
     * Get a CLI command by its name (case-insensitive).
     *
     * @param name the CLI command name
     * @return the ICliBotCommand instance, or null if not found
     */
    public ICliCommand get(String name) {
        if (name == null) return null;
        return cliCommands.stream()
                .filter(c -> c.matches(name))
                .findFirst()
                .orElse(null);
    }

}
