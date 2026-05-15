package top.chiloven.lukosbot2.core.command.cli;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.ICliCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class CliCmdRegistry {

    private final List<ICliCommand> cliCommands;
    private final Map<String, ICliCommand> index;

    public CliCmdRegistry(List<ICliCommand> iCliCommands) {
        this.cliCommands = iCliCommands;
        this.index = buildIndex(iCliCommands);
    }

    private static Map<String, ICliCommand> buildIndex(List<ICliCommand> commands) {
        var map = new LinkedHashMap<String, ICliCommand>();
        for (var cmd : commands) {
            register(map, cmd, cmd.name());
            for (var alias : cmd.aliases()) {
                register(map, cmd, alias);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, ICliCommand> map, ICliCommand cmd, String key) {
        var lower = key.toLowerCase();
        var existing = map.get(lower);
        if (existing != null) {
            log.warn(
                    "Duplicate CLI command key \"{}\": \"{}\" and \"{}\" both registered. "
                            + "Bean order determines which one is used.",
                    key,
                    existing.name(),
                    cmd.name()
            );
        } else {
            map.put(lower, cmd);
        }
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
     *
     * @return the ICliBotCommand instance, or null if not found
     */
    public ICliCommand get(String name) {
        if (name == null) return null;
        return index.get(name.toLowerCase());
    }

}
