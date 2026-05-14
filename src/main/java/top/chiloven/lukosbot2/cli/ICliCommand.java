package top.chiloven.lukosbot2.cli;

import top.chiloven.lukosbot2.commands.definition.CommandDefinition;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;

import java.util.List;

public interface ICliCommand {

    default boolean matches(String input) {
        if (input == null || input.isEmpty()) return false;
        if (name().equalsIgnoreCase(input)) return true;
        return aliases().stream()
                .anyMatch(alias -> alias.equalsIgnoreCase(input));
    }

    default String name() {
        return definition().getName();
    }

    default List<String> aliases() {
        return definition().getAliases();
    }

    CommandDefinition<CliCmdContext> definition();

    default String description() {
        return definition().getDescription();
    }

}
