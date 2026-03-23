package top.chiloven.lukosbot2.util.brigadier.builder;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;

/**
 * Factory for Brigadier {@link RequiredArgumentBuilder} bound to {@link CliCmdContext}.
 */
public final class CliRAB {

    private CliRAB() {
    }

    /**
     * Create a Brigadier {@code RequiredArgumentBuilder<CliCmdContext, T>} for the given name and type.
     *
     * @param name argument name
     * @param type argument type
     * @param <T>  value type
     * @return Brigadier required argument builder with source type {@link CliCmdContext}
     */
    public static <T> RequiredArgumentBuilder<CliCmdContext, T> argument(
            String name,
            ArgumentType<T> type
    ) {
        return RequiredArgumentBuilder.argument(name, type);
    }

}
