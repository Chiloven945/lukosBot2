package top.chiloven.lukosbot2.commands;

import com.mojang.brigadier.CommandDispatcher;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.UsageImageUtils;

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
 *   <li>{@link #usage()} describes the structured usage tree for this command and is
 *   typically shown when the user explicitly requests help for this command.</li>
 *   <li>{@link #sendUsage(CommandSource)} sends usage/help for this command using the same
 *   strategy as {@code /help}: send text when short enough, otherwise fall back to image.</li>
 *   <li>{@link #isVisible()} controls whether the command appears in generic
 *   help output; returning {@code false} keeps the command functional but
 *   hides it from listings.</li>
 * </ul>
 *
 * @author Chiloven945
 */
public interface IBotCommand {

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
     * Structured usage tree for this command.
     *
     * <p>HelpCommand and {@link #sendUsage(CommandSource, String, String)} will render this usage as
     * text or image depending on output constraints.</p>
     */
    UsageNode usage();

    /**
     * Send this command's usage/help output.
     *
     * <p>This uses the same mode parsing and AUTO heuristics as {@code HelpCommand} by delegating
     * to {@link UsageOutput}.</p>
     *
     * @param src     message sink
     * @param prefix  command prefix (e.g. "/"); if null/blank, "/" is used
     * @param modeRaw raw mode string (same as /help): "img"/"text"/null => AUTO
     */
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

    /**
     * Convenience: send usage with AUTO mode.
     *
     * @see #sendUsage(CommandSource, String, String)
     */
    default void sendUsage(CommandSource src, String prefix) {
        sendUsage(src, prefix, null);
    }

    /**
     * Convenience: send usage with default prefix "/".
     *
     * @see #sendUsage(CommandSource, String, String)
     */
    default void sendUsage(CommandSource src) {
        sendUsage(src, "/", null);
    }

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
