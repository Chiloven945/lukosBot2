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
 *   <li>{@code /pref [set] &lt;scope&gt; &lt;name&gt; &lt;value&gt;} persist value into explicit scope</li>
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

    String name();

    default String namespace() {
        return "prefs";
    }

    Class<T> type();

    EnumSet<ScopeType> allowedScopes();

    ScopeType preferredScope();

    /**
     * Scope resolution order for this state.
     *
     * <p>Default is {@code CHAT -> USER -> GLOBAL}. Definitions can override this to opt out of
     * user scope or to prefer user scope over chat scope.</p>
     */
    default List<ScopeType> resolveOrder() {
        return List.of(ScopeType.CHAT, ScopeType.USER, ScopeType.GLOBAL);
    }

    T defaultValue();

    T parse(String raw) throws IllegalArgumentException;

    default void validate(T value) throws IllegalArgumentException {
    }

    String format(T value);

    String description();

    default List<String> suggestValues() {
        return List.of();
    }

    default Duration ttl() {
        return null;
    }

}
