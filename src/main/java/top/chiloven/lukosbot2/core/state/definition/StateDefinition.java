package top.chiloven.lukosbot2.core.state.definition;

import top.chiloven.lukosbot2.core.state.ScopeType;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

/**
 * Definition of a persisted state value ("pref" / "memory") that can be read and modified by users via {@code /pref}.
 *
 * <h2>What is a state?</h2>
 * A {@code StateDefinition} describes a single logical key whose value is persisted in the unified state store (e.g.
 * H2/other DB) and can be resolved according to scope precedence.
 *
 * <p>Typical examples:</p>
 * <ul>
 *   <li>{@code lang}: reply language preference</li>
 *   <li>{@code status}: user's availability / bot interaction mode</li>
 *   <li>{@code cmd.github.defaultRepo}: command-specific default repository</li>
 * </ul>
 *
 * <h2>Storage model</h2>
 * The persisted value is stored as JSON under a tuple of:
 * <pre>
 * (scopeType, scopeId, namespace, name) -> JSON value
 * </pre>
 *
 * <p>The {@link #namespace()} + {@link #name()} pair identifies the key; {@link ScopeType} determines
 * where the value applies (global/user/chat).</p>
 *
 * <h2>Scope & precedence</h2>
 * The resolution of an effective value is handled by {@code StateService} (or equivalent component)
 * and is typically performed in this order (higher priority first):
 * <pre>
 * CHAT -> USER -> GLOBAL -> {@link #defaultValue()}
 * </pre>
 *
 * <p>Not every definition needs to support all scopes. Use {@link #allowedScopes()} to restrict
 * where values may be stored. When a scope is not allowed, the resolver should skip it.</p>
 *
 * <h2>Interaction through /pref</h2>
 * The {@code /pref} command usually supports:
 * <ul>
 *   <li>{@code /pref} list definitions (name + description + suggestions)</li>
 *   <li>{@code /pref &lt;name&gt;} show resolved value</li>
 *   <li>{@code /pref [set] &lt;name&gt; &lt;value&gt;} persist value into {@link #preferredScope()}
 *       (or into an explicit scope if such syntax is added later)</li>
 * </ul>
 *
 * <p>This interface is intentionally focused on "definition" (metadata + parse/format/validate).
 * The actual persistence, scope resolution, and command wiring are handled elsewhere.</p>
 *
 * <h2>Parsing & validation contract</h2>
 * Implementations should follow this flow:
 * <ol>
 *   <li>{@link #parse(String)} converts raw user input into a typed value {@code T}.</li>
 *   <li>{@link #validate(Object)} checks semantic correctness (range, enum membership, regex, etc.).</li>
 *   <li>The value is serialized to JSON and stored by the state store.</li>
 *   <li>{@link #format(Object)} formats a typed value for display back to the user.</li>
 * </ol>
 *
 * <p>Both {@link #parse(String)} and {@link #validate(Object)} may throw {@link IllegalArgumentException}
 * to indicate user input is invalid. Callers should convert that into a user-friendly error message.</p>
 *
 * <h2>TTL (temporary states)</h2>
 * Some states are intended to expire automatically (e.g. "busy for 2 hours").
 * Implementations may return a non-null {@link #ttl()} to instruct the persistence layer to
 * store an expiration timestamp. Resolvers should treat expired values as absent.
 *
 * <p>TTL is <b>optional</b>. A {@code null} TTL indicates the value does not expire.</p>
 *
 * <h2>Implementation guidelines</h2>
 * <ul>
 *   <li><b>Stability:</b> avoid changing {@link #name()} once published, as it becomes a persistent key.</li>
 *   <li><b>Namespace:</b> keep common user preferences in {@code "prefs"}; use {@code "cmd.*"} for
 *       command-specific defaults to avoid collisions.</li>
 *   <li><b>Suggestions:</b> provide {@link #suggestValues()} for enums to improve usability and completion.</li>
 *   <li><b>Type:</b> {@link #type()} should match the runtime type serialized to JSON for correct parsing.</li>
 * </ul>
 *
 * @param <T> the typed Java value of this state, which will be serialized/deserialized as JSON.
 */
public interface StateDefinition<T> {

    /**
     * Stable identifier used in the {@code /pref} command and as the persisted key name.
     *
     * <p><b>Important:</b> treat this as a persistent identifier. Changing it will make existing
     * stored values unreachable (effectively "resetting" user prefs).</p>
     *
     * @return the command-visible key name, e.g. {@code "lang"} or {@code "cmd.github.defaultRepo"}.
     */
    String name();

    /**
     * Logical grouping of states. Namespaces prevent collisions between unrelated features.
     *
     * <p>Conventions (recommended):</p>
     * <ul>
     *   <li>{@code "prefs"} for general user/chat preferences</li>
     *   <li>{@code "cmd.&lt;command&gt;"} for command-specific defaults</li>
     *   <li>{@code "service"} reserved for service states (enabled/config), not typically exposed via /pref</li>
     * </ul>
     *
     * @return namespace string, defaults to {@code "prefs"}.
     */
    default String namespace() {
        return "prefs";
    }

    /**
     * Runtime Java type for JSON deserialization.
     *
     * <p>This is used by the persistence/resolution layer to parse the stored JSON back into {@code T}.
     * For simple types (String/Integer/Boolean/record/POJO) {@code Class<T>} is sufficient. If you later need generic
     * types (e.g. {@code List<String>}), consider extending the API to accept a {@code TypeToken} /
     * {@code java.lang.reflect.Type} instead.</p>
     *
     * @return the concrete class of {@code T}.
     */
    Class<T> type();

    /**
     * Scopes where this state is allowed to be persisted.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Language could allow {@code GLOBAL/USER/CHAT}</li>
     *   <li>Per-user status might allow {@code USER} only</li>
     *   <li>A global feature flag might allow {@code GLOBAL} only</li>
     * </ul>
     *
     * <p>Callers should reject writes to scopes not included in this set, and resolvers should skip
     * disallowed scopes when resolving effective values.</p>
     *
     * @return allowed scope types.
     */
    EnumSet<ScopeType> allowedScopes();

    /**
     * Preferred scope for implicit writes.
     *
     * <p>When a user runs {@code /pref [set] <name> <value>} without explicitly specifying a scope,
     * the command should store the value into this preferred scope (as long as it is included in
     * {@link #allowedScopes()}).</p>
     *
     * <p>Recommended defaults:</p>
     * <ul>
     *   <li>{@code USER} for personal preferences (language, status)</li>
     *   <li>{@code CHAT} for group-specific defaults (e.g. "quiet mode" for a group)</li>
     *   <li>{@code GLOBAL} only for admin-level defaults</li>
     * </ul>
     *
     * @return preferred scope type for implicit writes.
     */
    ScopeType preferredScope();

    /**
     * Fallback value used when no persisted value exists in any applicable scope.
     *
     * <p>This value should be deterministic and ideally sourced from application configuration
     * (e.g. {@code AppProperties}) rather than being hard-coded.</p>
     *
     * @return default value.
     */
    T defaultValue();

    /**
     * Parse raw user input (as typed in {@code /pref}) into a typed value.
     *
     * <p>Implementations should be permissive with whitespace/case where appropriate and provide
     * clear error messages via {@link IllegalArgumentException}.</p>
     *
     * @param raw raw user-supplied string (not null).
     * @return parsed typed value.
     * @throws IllegalArgumentException if the input cannot be parsed.
     */
    T parse(String raw) throws IllegalArgumentException;

    /**
     * Validate the parsed value semantically.
     *
     * <p>This method is invoked after {@link #parse(String)} and before persistence. Use it to enforce
     * domain constraints (e.g. allowed enum values, numeric ranges, max length, etc.).</p>
     *
     * <p>Default implementation performs no validation.</p>
     *
     * @param value parsed value.
     * @throws IllegalArgumentException if the value is not acceptable.
     */
    default void validate(T value) throws IllegalArgumentException {
    }

    /**
     * Format a typed value into a user-facing string for display.
     *
     * <p>This is used by {@code /pref <name>} or confirmation messages after setting a value.</p>
     *
     * @param value typed value.
     * @return user-facing string representation.
     */
    String format(T value);

    /**
     * Short user-facing description shown in {@code /pref} listing output.
     *
     * <p>Keep it concise; include key constraints or examples if helpful.</p>
     *
     * @return description of the state.
     */
    String description();

    /**
     * Optional suggestions for value completion / help output.
     *
     * <p>Useful for enum-like states such as languages. The command layer may use these to provide
     * tab completion or to show hints.</p>
     *
     * @return list of suggested raw values; default is empty.
     */
    default List<String> suggestValues() {
        return List.of();
    }

    /**
     * Optional TTL for temporary states.
     *
     * <p>If non-null, the persistence layer should set an expiration time of {@code now + ttl}.
     * After expiration, the state should be treated as absent during resolution.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code status="busy"} with TTL of 2 hours</li>
     *   <li>A command "cooldown" state with TTL of 30 seconds</li>
     * </ul>
     *
     * @return time-to-live duration, or {@code null} for non-expiring states.
     */
    default Duration ttl() {
        return null;
    }

}
