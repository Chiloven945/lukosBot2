package top.chiloven.lukosbot2.commands;

import top.chiloven.lukosbot2.commands.definition.CommandDefinition;
import top.chiloven.lukosbot2.commands.definition.bridge.CommandUsageMapper;
import top.chiloven.lukosbot2.core.command.CommandSource;

import java.util.List;

public interface IBotCommand {

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

    CommandDefinition<CommandSource> definition();

    default String description() {
        return definition().getDescription();
    }

    default void sendUsage(CommandSource src, String prefix) {
        sendUsage(src, prefix, null);
    }

    default void sendUsage(CommandSource src, String prefix, String modeRaw) {
        UsageOutput.UseMode mode = UsageOutput.parseMode(modeRaw);
        UsageTextRenderer.Options opt = UsageTextRenderer.Options.forHelp(
                (prefix == null || prefix.isBlank()) ? "/" : prefix.trim()
        );

        UsageOutput.sendUsage(
                src,
                prefix,
                name(),
                usage(),
                opt,
                UsageImageUtils.ImageStyle.defaults(),
                mode
        );
    }

    default UsageNode usage() {
        return CommandUsageMapper.INSTANCE.toUsageNode(definition());
    }

    default void sendUsage(CommandSource src) {
        sendUsage(src, "/", null);
    }

    default boolean isVisible() {
        return definition().getVisible();
    }

}
