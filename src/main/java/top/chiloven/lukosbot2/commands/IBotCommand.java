package top.chiloven.lukosbot2.commands;

import top.chiloven.lukosbot2.core.command.bot.CommandSource;
import top.chiloven.lukosbot2.core.command.definition.CommandDefinition;
import top.chiloven.lukosbot2.core.command.definition.bridge.CommandUsageMapper;

import java.util.List;

/**
 * Contract for all chat-based bot commands.
 *
 * <h2>What is a bot command?</h2>
 * A {@code BotCommand} wraps a {@link CommandDefinition}{@code <CommandSource>} that declaratively describes the
 * command's name, aliases, description, structured usage tree, visibility, and execution logic.
 *
 * <p>Commands are discovered automatically by Spring via {@link org.springframework.stereotype.Service}
 * and registered into the {@code CommandRegistry}. The {@code CommandProcessor} strips the global prefix (e.g.
 * {@code "/"}), looks up the matching command by name or alias, and delegates execution to
 * {@code BotCommandRuntime}.</p>
 *
 * <h2>Implementing a command</h2>
 * The only required method is {@link #definition()}. All other accessors ({@link #name()}, {@link #aliases()},
 * {@link #description()}, {@link #usage()}, {@link #isVisible()}) derive from the definition by default.
 *
 * <p>Minimal example (Kotlin):</p>
 * <pre>{@code
 * @Service
 * class PingCommand : IBotCommand {
 *     private val cmd = botCommand("ping") {
 *         description = "Check bot status"
 *         execute { source.reply("pong!") }
 *     }
 *     override fun definition() = cmd
 * }
 * }</pre>
 *
 * <h2>Spring integration</h2>
 * Implementations are typically declared as Spring beans via {@link org.springframework.stereotype.Service} and can be
 * toggled via {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} with the prefix
 * {@code lukos.commands.control}:
 *
 * <pre>{@code
 * @Service
 * @ConditionalOnProperty(
 *     prefix = "lukos.commands.control",
 *     name = ["dice"],
 *     havingValue = "true",
 *     matchIfMissing = true
 * )
 * class DiceCommand : IBotCommand { ... }
 * }</pre>
 *
 * <h2>Visibility & help output</h2>
 * Commands whose {@link #isVisible()} returns {@code false} remain executable by name but are excluded from generic
 * listings such as {@code /help}. This is useful for internal or deprecated commands that should not clutter the help
 * output.
 *
 * <h2>Usage rendering</h2>
 * The {@link #usage()} method constructs a {@link UsageNode} tree from the command's {@code CommandDefinition} via
 * {@link CommandUsageMapper}. The help system ({@link UsageOutput}) decides whether to render as plain text or as an
 * image based on output length and the user's requested mode.
 *
 * <h2>Policy & permissions</h2>
 * Permission checks are handled externally by {@code CommandProcessor} via {@code PolicyService}. Command
 * implementations should not perform their own permission checks; they can assume the caller has already been
 * authorized.
 *
 * @author Chiloven945
 */
public interface IBotCommand {

    /**
     * Returns whether the given input matches this command's canonical name or any of its aliases.
     *
     * <p>Matching is case-insensitive. Returns {@code false} for {@code null}
     * or empty input. This method is used by the command registry for command lookup and by the runtime for verifying
     * the root token.</p>
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
     * Returns the primary, user-facing command label.
     *
     * <p>This name is used as the canonical lookup key in the command registry and
     * as the root literal in the usage tree. It should be unique across all registered bot commands.</p>
     *
     * <p>Derived from {@link #definition()} by default; override to provide a
     * fixed name when not using the DSL.</p>
     *
     * @return canonical command name, e.g. {@code "ping"} or {@code "github"}.
     */
    default String name() {
        return definition().getName();
    }

    /**
     * Returns alternative invocation names for this command.
     *
     * <p>Aliases are matched case-insensitively by {@link #matches(String)} and by the
     * command registry during command lookup. Using aliases can greatly improve user experience for frequently-used or
     * long command names.</p>
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
     * <p>This is the single required method. All other accessors ({@link #name()},
     * {@link #aliases()}, {@link #description()}, {@link #usage()}, {@link #isVisible()}) derive from the returned
     * definition by default.</p>
     *
     * <p><b>Performance note:</b> command authors should store the definition in a
     * private field to avoid re-creating the DSL tree on every access:</p>
     *
     * <pre>{@code
     * private val cmd = botCommand("ping") { ... }
     * override fun definition() = cmd
     * }</pre>
     *
     * @return the command definition describing name, structure, and execution logic.
     */
    CommandDefinition<CommandSource> definition();

    /**
     * Returns a short, human-readable description of this command.
     *
     * <p>Used in {@code /help} listings, command overviews, and inline hints.
     * Should fit on one line; detailed syntax information belongs in the usage tree.</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return brief description, never {@code null}.
     */
    default String description() {
        return definition().getDescription();
    }

    /**
     * Convenience overload that sends usage with auto mode detection.
     *
     * <p>Equivalent to calling {@link #sendUsage(CommandSource, String, String)
     * sendUsage(src, prefix, null)}.</p>
     *
     * @param src    message sink for replies.
     * @param prefix command prefix, e.g. {@code "/"}.
     */
    default void sendUsage(CommandSource src, String prefix) {
        /**
         * Sends this command's usage/help output to the given source using the
         * specified command prefix and output mode.
         *
         * <p>Delegates to {@link UsageOutput#sendUsage} which automatically decides
         * between text and image rendering based on output size constraints. The
         * mode parameter allows the caller to override this default:</p>
         * <ul>
         *   <li>{@code "img"} — force image rendering (better for long output)</li>
         *   <li>{@code "text"} — force text rendering</li>
         *   <li>{@code null} — auto-detect based on content length</li>
         * </ul>
         *
         * @param src     message sink for replies.
         * @param prefix  command prefix, e.g. {@code "/"}; {@code null} or blank
         *                defaults to {@code "/"}.
         * @param modeRaw raw mode string as described above, or {@code null} for auto.
         */
        sendUsage(src, prefix, null);
    }

    /**
     * Sends this command's usage/help output to the given source using the specified command prefix and output mode.
     *
     * <p>Delegates to {@link UsageOutput#sendUsage} which automatically decides
     * between text and image rendering based on output size constraints. The mode parameter allows the caller to
     * override this default:</p>
     * <ul>
     *   <li>{@code "img"} — force image rendering (better for long output)</li>
     *   <li>{@code "text"} — force text rendering</li>
     *   <li>{@code null} — auto-detect based on content length</li>
     * </ul>
     *
     * @param src     message sink for replies.
     * @param prefix  command prefix, e.g. {@code "/"}; {@code null} or blank defaults to {@code "/"}.
     * @param modeRaw raw mode string as described above, or {@code null} for auto.
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
     * Returns the structured usage tree for this command.
     *
     * <p>The resulting {@link UsageNode} tree is constructed from the command's
     * {@code CommandDefinition} by {@link CommandUsageMapper}. It contains syntax lines, parameter/option
     * documentation, examples, notes, and recursive subcommand nodes.</p>
     *
     * <p>The help system ({@link UsageOutput}) renders this tree as text or as an
     * image depending on output length and the user's requested mode. Commands should not implement or call this method
     * directly; use {@link #sendUsage(CommandSource)} to display help to a user.</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return structured usage specification, never {@code null}.
     */
    default UsageNode usage() {
        return CommandUsageMapper.INSTANCE.toUsageNode(definition());
    }

    /**
     * Convenience overload that sends usage with the default prefix {@code "/"} and auto mode detection.
     *
     * <p>Equivalent to calling {@link #sendUsage(CommandSource, String, String)
     * sendUsage(src, "/", null)}.</p>
     *
     * @param src message sink for replies.
     */
    default void sendUsage(CommandSource src) {
        /**
         * Sends this command's usage/help output to the given source using the
         * specified command prefix and output mode.
         *
         * <p>Delegates to {@link UsageOutput#sendUsage} which automatically decides
         * between text and image rendering based on output size constraints. The
         * mode parameter allows the caller to override this default:</p>
         * <ul>
         *   <li>{@code "img"} — force image rendering (better for long output)</li>
         *   <li>{@code "text"} — force text rendering</li>
         *   <li>{@code null} — auto-detect based on content length</li>
         * </ul>
         *
         * @param src     message sink for replies.
         * @param prefix  command prefix, e.g. {@code "/"}; {@code null} or blank
         *                defaults to {@code "/"}.
         * @param modeRaw raw mode string as described above, or {@code null} for auto.
         */
        sendUsage(src, "/", null);
    }

    /**
     * Returns whether this command should appear in generic help output such as the {@code /help} command listing.
     *
     * <p>A hidden command ({@code isVisible() == false}) remains executable
     * by name and via aliases, but is omitted from {@code /help} output and command list overviews. This is useful for
     * internal or transitional commands.</p>
     *
     * <p>Derived from {@link #definition()} by default.</p>
     *
     * @return {@code true} if the command is visible in help output.
     */
    default boolean isVisible() {
        return definition().getVisible();
    }

}
