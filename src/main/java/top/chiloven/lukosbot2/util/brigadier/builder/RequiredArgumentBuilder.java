package top.chiloven.lukosbot2.util.brigadier.builder;

import top.chiloven.lukosbot2.core.command.CommandSource;
import com.mojang.brigadier.arguments.ArgumentType;

/**
 * Factory for Brigadier required argument builders bound to {@link CommandSource}.
 */
public final class RequiredArgumentBuilder {

    private RequiredArgumentBuilder() {
    }

    /**
     * Create a Brigadier {@code RequiredArgumentBuilder<CommandSource, T>} for the given name and type.
     *
     * @param name argument name
     * @param type argument type
     * @param <T>  value type
     * @return Brigadier required argument builder with source type {@link CommandSource}
     */
    public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSource, T> argument(
            String name,
            ArgumentType<T> type
    ) {
        return com.mojang.brigadier.builder.RequiredArgumentBuilder.argument(name, type);
    }
}
