package top.chiloven.lukosbot2.util.brigadier.builder;

import top.chiloven.lukosbot2.core.command.CommandSource;

/**
 * Literal argument builder with source type fixed to {@link CommandSource}.
 *
 * <p>This class extends Brigadier's {@link com.mojang.brigadier.builder.LiteralArgumentBuilder}
 * so all original fluent methods are available.</p>
 */
public final class LiteralArgumentBuilder
        extends com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> {

    private LiteralArgumentBuilder(String literal) {
        super(literal);
    }

    /**
     * Create a new literal builder for the given name.
     *
     * @param name literal name
     * @return builder instance
     */
    public static LiteralArgumentBuilder literal(String name) {
        return new LiteralArgumentBuilder(name);
    }
}
