package top.chiloven.lukosbot2.cli;

import com.mojang.brigadier.CommandDispatcher;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Contract for all CLI commands.
 *
 * <p>A {@code CliCommand} is a thin wrapper around one or more Brigadier nodes
 * that share a common primary name. Implementations are responsible for registering their own syntax tree into the
 * shared {@link CommandDispatcher} used by the console command subsystem.</p>
 *
 * <h2>Spring integration</h2>
 *
 * <p>Implementations are typically declared as Spring beans using
 * {@link org.springframework.stereotype.Service}. This allows the application to auto-discover and register all CLI
 * commands at startup.</p>
 *
 * <p>Whether a command is enabled can be controlled via
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}. A common pattern is to gate the bean
 * on a configuration switch:</p>
 *
 * <pre>{@code
 * @Service
 * @ConditionalOnProperty(
 *         prefix = "lukos.cli.control",
 *         name = "my-command",
 *         havingValue = "true",
 *         matchIfMissing = true
 * )
 * public class MyCliCommand implements ICliCommand {
 *     ...
 * }
 * }</pre>
 *
 * <p>With the above configuration, the command is enabled by default and can be
 * disabled by setting:</p>
 *
 * <pre><code>
 * lukos.cli.commands.my-command=false
 * </code></pre>
 *
 * <h2>Naming and usage</h2>
 *
 * <ul>
 *   <li>{@link #name()} should return the primary, user-facing command label
 *   (for example {@code "help"}, {@code "send"}, {@code "shutdown"}). It is
 *   expected to be unique within the CLI dispatcher.</li>
 *   <li>{@link #aliases()} return a List of aliases of the command.</li>
 *   <li>{@link #description()} is a short, one-line summary suitable for help
 *   listings.</li>
 *   <li>{@link #usage()} describes the structured usage syntax for this command and is
 *   typically shown when the user explicitly requests help for this command or
 *   when argument parsing fails.</li>
 * </ul>
 *
 * <h2>Registration</h2>
 *
 * <p>Implementations should register their Brigadier syntax tree under the
 * shared CLI {@link CommandDispatcher}. The dispatcher source type is
 * {@link CliCmdContext}, which represents the execution context of a console
 * command.</p>
 *
 * @author Chiloven945
 */
public interface ICliCommand {

    /**
     * Returns the primary name of this CLI command.
     *
     * <p>The name is used as the root literal in the CLI command tree and should
     * be unique across all registered CLI commands.</p>
     *
     * @return the main name of the command
     */
    String name();

    /**
     * Returns a list of aliases of this CLI command.
     *
     * <p>This is a default method which will return an empty {@link ArrayList}. Specify the aliases by override this method.</p>
     *
     * @return a list of the aliases
     */
    default List<String> aliases() {
        return new ArrayList<>();
    }

    default boolean matches(String input) {
        if (input == null || input.isEmpty()) return false;
        if (name().equalsIgnoreCase(input)) return true;
        return aliases().stream()
                .anyMatch(alias -> alias.equalsIgnoreCase(input));
    }

    /**
     * Returns a short, human-readable description of this CLI command.
     *
     * <p>This text is typically used in CLI help overviews or command listings.</p>
     *
     * @return brief description of the command
     */
    String description();

    /**
     * Returns the usage syntax of this CLI command.
     *
     * <p>This is typically a concise textual form such as
     * {@code "send <platform>:<p|g>:<id> <text>"} and is suitable for displaying in help output or validation
     * errors.</p>
     *
     * @return usage syntax of the command
     */
    String usage();

    /**
     * Registers this CLI command and all of its sub-nodes with the given dispatcher.
     *
     * <p>Implementations should call Brigadier builder APIs (such as
     * {@code literal(...)} and {@code argument(...)}) and then register the resulting tree under {@link #name()}.</p>
     *
     * @param dispatcher the shared CLI command dispatcher
     */
    void register(CommandDispatcher<CliCmdContext> dispatcher);

}
