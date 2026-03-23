package top.chiloven.lukosbot2.util.brigadier.builder;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import top.chiloven.lukosbot2.core.command.CommandSource;

/**
 * Factory for Brigadier {@link RequiredArgumentBuilder} bound to {@link CommandSource}.
 */
public final class CommandRAB {

    private CommandRAB() {
    }

    /**
     * Create a Brigadier {@code RequiredArgumentBuilder<CommandSource, T>} for the given name and type.
     *
     * @param name argument name
     * @param type argument type
     * @param <T>  value type
     * @return Brigadier required argument builder with source type {@link CommandSource}
     */
    public static <T> RequiredArgumentBuilder<CommandSource, T> argument(
            String name,
            ArgumentType<T> type
    ) {
        return RequiredArgumentBuilder.argument(name, type);
    }

}
