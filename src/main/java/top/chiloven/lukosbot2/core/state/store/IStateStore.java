package top.chiloven.lukosbot2.core.state.store;

import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.ScopeType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Unified persistent key-value store for bot states.
 *
 * <p>Store values as JSON strings so we can persist arbitrary objects (prefs, service states, command states...).</p>
 */
public interface IStateStore {

    Optional<String> getJson(
            Scope scope,
            String namespace,
            String key
    );

    /**
     * Get all keys under the given namespace in the given scope.
     */
    Map<String, String> getNamespaceJson(
            Scope scope,
            String namespace
    );

    /**
     * Upsert a json value.
     *
     * @param expiresAtOrNull optional expiry timestamp; if null the record never expires.
     */
    void upsertJson(
            Scope scope,
            String namespace,
            String key,
            String json,
            Instant expiresAtOrNull
    );

    void delete(
            Scope scope,
            String namespace,
            String key
    );

    /**
     * Scan all records for a given scope type and namespace.
     *
     * @return scope_id -> (key -> json)
     */
    Map<String, Map<String, String>> scanByScopeTypeAndNamespace(
            ScopeType type,
            String namespace
    );

}
