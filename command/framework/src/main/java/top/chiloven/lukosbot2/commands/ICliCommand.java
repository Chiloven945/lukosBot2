package top.chiloven.lukosbot2.commands;

import top.chiloven.lukosbot2.core.command.cli.CliCmdContext;
import top.chiloven.lukosbot2.core.command.definition.CommandDefinition;

import java.util.List;

/**
 * Contract for all console (CLI) commands.
 *
 * <h2>What is a CLI command?</h2>
 * A CLI command wraps a {@link CommandDefinition}{@code <CliCmdContext>} that declaratively describes the command's
 * name, aliases, description, and execution logic through the project's shared command definition DSL. CLI commands are
 * entirely independent of the bot subsystem — they do not implement {@code IBotCommand} and do not participate in bot
 * permission checks, usage image rendering, or the {@code CommandSource} reply pipeline.
 *
 * <h2>Execution model</h2>
 * CLI commands use {@link CliCmdContext} as their source type. This context provides a custom
 * {@link java.io.PrintStream} with color-code resolution (e.g. {@code "§2"} for green, {@code "§4"} for red) and
 * convenience methods like {@link CliCmdContext#println} and {@link CliCmdContext#printlnErr}.
 *
 * <p>Commands are discovered automatically by Spring via
 * {@link org.springframework.stereotype.Service} and registered into the {@code CliCmdRegistry}. The
 * {@code CliCmdProcessor} reads input lines from the console, looks up the matching command, and delegates execution to
 * {@code CliCommandRuntime}.</p>
 *
 * <h2>Implementing a CLI command</h2>
 * The only required method is {@link #definition()}. All other accessors ({@link #name()}, {@link #aliases()},
 * {@link #description()}) derive from the definition by default.
 *
 * <p>Minimal example (Kotlin):</p>
 * <pre>{@code
 * @Service
 * @ConditionalOnProperty(
 *     prefix = "lukos.cli.control",
 *     name = ["shutdown"],
 *     havingValue = "true",
 *     matchIfMissing = true
 * )
 * class ShutdownCliCommand : ICliCommand {
 *     private val cmd = cliCommand("shutdown") {
 *         alias("stop", "close")
 *         description = "Shutdown the bot process"
 *         execute { Thread.ofVirtual().start { Main.shutdown() } }
 *     }
 *     override fun definition() = cmd
 * }
 * }</pre>
 *
 * <h2>Differences from bot commands</h2>
 * CLI commands differ from {@code IBotCommand} in several key ways:
 * <ul>
 *   <li><b>Source type:</b> {@link CliCmdContext} instead of {@code CommandSource}</li>
 *   <li><b>Output:</b> direct console output via {@code println} instead of the
 *       {@code reply(text)} / outbound message pipeline</li>
 *   <li><b>No usage rendering:</b> CLI does not render {@code UsageNode} as images</li>
 *   <li><b>No permission checks:</b> CLI assumes the operator is a super-user</li>
 *   <li><b>No prefix:</b> CLI input does not use the bot prefix {@code "/"}</li>
 *   <li><b>DSL entry:</b> uses {@code cliCommand(...)} instead of {@code botCommand(...)}
 *       to produce {@code CommandDefinition<CliCmdContext>}</li>
 * </ul>
 *
 * @author Chiloven945
 */
public interface ICliCommand {

    /**
     * Returns whether the given input matches this command's canonical name or any of its aliases.
     *
     * <p>Matching is case-insensitive. Returns {@code false} for {@code null}
     * or empty input. Used by the CLI command registry and runtime for command lookup and root token verification.</p>
     *
     * @param input raw command token to check.
     *
     * @return {@code true} if the input matches the canonical name or an alias.
     */
    default boolean matches(String input) {
        if (input == null || input.isEmpty()) return false;
        if (name().equalsIgnoreCase(input)) return true;
        return aliases().stream()
                .anyMatch(alias -> alias.equalsIgnoreCase(input));
    }

    /**
     * Returns the primary command label as typed in the console.
     *
     * <p>This name is used as the canonical lookup key in the CLI command registry.
     * It should be a short, lowercase token without spaces (e.g. {@code "reload"}, {@code "shutdown"}).</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return canonical command name.
     */
    default String name() {
        return definition().getName();
    }

    /**
     * Returns alternative invocation names for this command.
     *
     * <p>Aliases are matched case-insensitively by {@link #matches(String)} and
     * by the CLI command registry during command lookup. Aliases share the same execution logic as the canonical
     * name.</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return immutable list of aliases; may be empty.
     */
    default List<String> aliases() {
        return definition().getAliases();
    }

    /**
     * Returns the structured command definition that powers all derived accessors.
     *
     * <p>This is the single required method. All other accessors
     * ({@link #name()}, {@link #aliases()}, {@link #description()}) derive from the returned definition by
     * default.</p>
     *
     * <p><b>Performance note:</b> store the definition in a private field
     * to avoid re-creating the DSL tree on every access:</p>
     *
     * <pre>{@code
     * private val cmd = cliCommand("reload") { ... }
     * override fun definition() = cmd
     * }</pre>
     *
     * @return the CLI command definition.
     */
    CommandDefinition<CliCmdContext> definition();

    /**
     * Returns a short, human-readable description of this command.
     *
     * <p>Used in CLI help output and command listings. Should fit on one line.</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return brief description, never {@code null}.
     */
    default String description() {
        return definition().getDescription();
    }

}
