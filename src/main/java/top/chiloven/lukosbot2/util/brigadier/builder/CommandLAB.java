package top.chiloven.lukosbot2.util.brigadier.builder;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import top.chiloven.lukosbot2.core.command.CommandSource;

/**
 * Literal argument builder with source type fixed to {@link CommandSource}.
 *
 * <p>This class extends Brigadier's {@link com.mojang.brigadier.builder.LiteralArgumentBuilder}
 * so all original fluent methods are available.</p>
 */
public final class CommandLAB extends LiteralArgumentBuilder<CommandSource> {

    private CommandLAB(String literal) {
        super(literal);
    }

    /**
     * Create a new literal builder for the given name.
     *
     * @param name literal name
     * @return builder instance
     */
    public static CommandLAB literal(String name) {
        return new CommandLAB(name);
    }

}
