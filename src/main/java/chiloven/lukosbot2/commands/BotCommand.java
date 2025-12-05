package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;

/**
 * Contract for all bot commands.
 *
 * <p>A {@code BotCommand} is a thin wrapper around one or more
 * Brigadier nodes that share a common primary name. Implementations are
 * responsible for registering their own syntax tree into the shared
 * {@link CommandDispatcher}.</p>
 *
 * <h2>Spring integration</h2>
 *
 * <p>Implementations are typically declared as Spring beans using
 * {@link org.springframework.stereotype.Service}. This allows the
 * application to auto-discover and register all commands at startup.</p>
 *
 * <p>Whether a command is enabled can be controlled via
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}.
 * A common pattern is to gate the bean on a configuration switch:</p>
 *
 * <pre>{@code
 * @Service
 * @ConditionalOnProperty(
 *         prefix = "lukos.commands.control",
 *         name = "my-command",
 *         havingValue = "true",
 *         matchIfMissing = true
 * )
 * public class MyCommand implements BotCommand {
 *     ...
 * }
 * }</pre>
 *
 * <p>With the above configuration, the command is enabled by default and can be
 * disabled by setting:</p>
 *
 * <pre><code>
 * lukos.commands.control.my-command=false
 * </code></pre>
 *
 * <h2>Naming and visibility</h2>
 *
 * <ul>
 *   <li>{@link #name()} should return the primary, user-facing command label
 *   (for example {@code "help"}, {@code "dice"}). It is expected to be unique
 *   within the dispatcher.</li>
 *   <li>{@link #description()} is a short, one-line summary suitable for help
 *   listings.</li>
 *   <li>{@link #usage()} may contain multi-line usage examples and is typically
 *   shown when the user explicitly requests help for this command.</li>
 *   <li>{@link #isVisible()} controls whether the command appears in generic
 *   help output; returning {@code false} keeps the command functional but
 *   hides it from listings.</li>
 * </ul>
 *
 * @author Chiloven945
 */
public interface BotCommand {

    /**
     * Returns the primary name of this command.
     * <p>The name is used as the root literal in the command tree and should be
     * unique across all registered commands.</p>
     *
     * @return the main name of the command
     */
    String name();

    /**
     * Returns a short, human-readable description of this command.
     * <p>This text is typically used in help overviews or command listings.</p>
     *
     * @return brief description of the command
     */
    String description();

    /**
     * Returns the detailed usage text for this command.
     * <p>Implementations may return multi-line text including syntax and
     * examples. It is usually shown when the user explicitly asks for help
     * on this command.</p>
     *
     * @return detailed usage information
     */
    String usage();

    /**
     * Registers this command and all of its sub-nodes with the given dispatcher.
     * <p>Implementations should call Brigadier builder APIs (such as
     * {@code literal(...)} and {@code argument(...)}) and then register the
     * resulting tree under {@link #name()}.</p>
     *
     * @param dispatcher the global command dispatcher used by the bot
     */
    void register(CommandDispatcher<CommandSource> dispatcher);

    /**
     * Indicates whether this command should be shown in generic help output.
     *
     * <p>Returning {@code false} hides the command from listings such as
     * {@code /help}, while still allowing it to be executed if the user knows
     * the exact name.</p>
     *
     * @return {@code true} if the command should be visible in help, {@code false} otherwise
     */
    default boolean isVisible() {
        return true;
    }
}
