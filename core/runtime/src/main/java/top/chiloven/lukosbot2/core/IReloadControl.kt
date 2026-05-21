package top.chiloven.lukosbot2.core

/**
 * Control interface for reloading bot subsystems without restarting the JVM process.
 *
 * ## What is IReloadControl?
 * `IReloadControl` defines the reload contract consumed by CLI commands such as `ReloadCliCommand`.
 * The implementation lives in `:app` (`ReloadManager`) and combines hot-reloading of individual
 * platform adapters or config with full-process restart via [IApplicationControl].
 *
 * ## Why does this interface exist?
 * Before modularization, `ReloadCliCommand` directly depended on `ReloadManager` in the `core`
 * package, which in turn depended on concrete lifecycle classes (`TelegramLifecycle`,
 * `DiscordLifecycle`, `OneBotLifecycle`, `ConfigLifecycle`) and `Main.restart()` — creating
 * a reverse dependency from `core` → `app`.
 *
 * By extracting `IReloadControl` into `:core:runtime`, CLI commands stay decoupled from the
 * application layer. `ReloadManager` (`:app`) implements this interface while retaining access to
 * all lifecycle components.
 *
 * ## Thread safety
 * All methods are expected to be thread-safe. Implementations should serialize concurrent reload
 * requests via a lock or queue. A `ReentrantLock` (see `ReloadManager`) is the recommended approach.
 *
 * ## Supported modules
 * The [reloadModules] method accepts the following module names (case-insensitive, with aliases):
 *
 * - `"config"`, `"conf"`, `"cf"` — reload the bot configuration layer.
 * - `"telegram"`, `"tg"` — restart the Telegram long-polling bot session.
 * - `"discord"`, `"dc"` — restart the Discord gateway connection.
 * - `"onebot"`, `"ob"`, `"qq"` — restart the OneBot WebSocket connection.
 * - `"bot"`, `"all"` — full process restart via [IApplicationControl.restart].
 *
 * Any unrecognized module name results in an [IllegalArgumentException].
 */
interface IReloadControl {

    /**
     * Restarts the entire bot process by delegating to [IApplicationControl.restart].
     *
     * This is equivalent to passing `"bot"` or `"all"` to [reloadModules],
     * but is offered as a convenience method for simple "reload everything" use cases
     * that do not require module-name parsing.
     *
     * The restart involves closing the Spring application context and launching a new one.
     * All platform connections will be briefly interrupted and then re-established.
     *
     * @see IApplicationControl.restart
     */
    fun reloadWholeBot()

    /**
     * Reloads one or more named modules without restarting the entire process.
     *
     * Modules that map to individual platform adapters (`"telegram"`, `"discord"`, `"onebot"`)
     * are hot-reloaded by stopping and restarting their lifecycle beans via [org.springframework.context.SmartLifecycle].
     * The corresponding platform sender is unregistered from [MessageSenderHub] before the
     * lifecycle restart, ensuring no stale outbound routes remain.
     *
     * The `"config"` module restarts `ConfigLifecycle`, which re-reads `application.yml`
     * and refreshes all `@ConfigurationProperties` beans.
     *
     * The `"bot"` and `"all"` modules trigger a full process restart
     * (delegating to [IApplicationControl.restart]) rather than individual lifecycle cycling.
     *
     * Aliases are accepted for each module — see the class-level documentation for the
     * supported name mappings.
     *
     * @param names a collection of raw module name strings, which will be trimmed,
     *              lowercased, and deduplicated internally. Must not be empty.
     *
     * @return the deduplicated, normalised list of modules that were successfully reloaded.
     *
     * @throws IllegalArgumentException if `names` is empty, or if any name does not
     *         match a supported module (after trimming and lowercasing).
     *
     * @see ReloadManager.supportedModules for the full list of recognised module names.
     */
    fun reloadModules(names: Collection<String>): List<String>

}
