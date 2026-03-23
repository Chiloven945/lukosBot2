package top.chiloven.lukosbot2.util.brigadier.builder;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;

/**
 * Literal argument builder with source type fixed to {@link CliCmdContext}.
 *
 * <p>This class extends Brigadier's {@link LiteralArgumentBuilder}
 * so all original fluent methods are available.</p>
 */
public final class CliLAB extends LiteralArgumentBuilder<CliCmdContext> {

    private CliLAB(String literal) {
        super(literal);
    }

    /**
     * Create a new literal builder for the given name.
     *
     * @param name literal name
     * @return builder instance
     */
    public static CliLAB literal(String name) {
        return new CliLAB(name);
    }

}
