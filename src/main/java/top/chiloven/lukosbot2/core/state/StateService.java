package top.chiloven.lukosbot2.core.state;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import top.chiloven.lukosbot2.core.state.definition.StateDefinition;
import top.chiloven.lukosbot2.core.state.store.IStateStore;
import top.chiloven.lukosbot2.model.message.Address;

import java.time.Instant;

/**
 * High-level service for reading/writing {@link StateDefinition} values.
 */
@Service
@Log4j2
public class StateService {

    private final IStateStore store;
    private final JsonMapper mapper;

    public StateService(IStateStore store, JsonMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public <T> T resolve(StateDefinition<T> def, Address addr, Long userId) {
        for (ScopeType type : def.resolveOrder()) {
            Scope scope = scopeByType(type, def, addr, userId);
            if (scope == null) continue;

            T value = getAtScope(def, scope);
            if (value != null) {
                return value;
            }
        }
        return def.defaultValue();
    }

    private Scope scopeByType(ScopeType type, StateDefinition<?> def, Address addr, Long userId) {
        if (type == null || !def.allowedScopes().contains(type)) return null;
        return switch (type) {
            case CHAT -> Scope.chat(addr);
            case USER -> (userId == null) ? null : Scope.user(addr.platform(), userId);
            case GLOBAL -> Scope.global();
        };
    }

    public <T> T getAtScope(StateDefinition<T> def, Scope scope) {
        if (def == null || scope == null) return null;
        if (!def.allowedScopes().contains(scope.type())) return null;

        var v = store.getJson(scope, def.namespace(), def.name()).orElse(null);
        if (v == null) return null;

        try {
            return mapper.readValue(v, def.type());
        } catch (Exception e) {
            log.debug("Failed to parse state {}.{} at {}: {}", def.namespace(), def.name(), scope.type(), e.getMessage());
            return null;
        }
    }

    public <T> void set(StateDefinition<T> def, Address addr, Long userId, String rawValue) {
        Scope scope = preferredScope(def, addr, userId);
        setAtScope(def, scope, rawValue);
    }

    public Scope preferredScope(StateDefinition<?> def, Address addr, Long userId) {
        Scope s = scopeByType(def.preferredScope(), def, addr, userId);
        if (s != null) return s;

        s = scopeByType(ScopeType.USER, def, addr, userId);
        if (s != null) return s;
        s = scopeByType(ScopeType.CHAT, def, addr, userId);
        if (s != null) return s;
        s = scopeByType(ScopeType.GLOBAL, def, addr, userId);
        if (s != null) return s;

        return Scope.chat(addr);
    }

    public <T> void setAtScope(StateDefinition<T> def, Scope scope, String rawValue) {
        if (rawValue == null) throw new IllegalArgumentException("value is null");
        if (scope == null) throw new IllegalArgumentException("scope is null");
        if (!def.allowedScopes().contains(scope.type())) {
            throw new IllegalArgumentException("scope " + scope.type() + " is not allowed for state " + def.name());
        }

        T v = def.parse(rawValue);
        def.validate(v);

        Instant exp = def.ttl() == null ? null : Instant.now().plus(def.ttl());
        store.upsertJson(scope, def.namespace(), def.name(), mapper.writeValueAsString(v), exp);
    }

    public void clear(StateDefinition<?> def, Address addr, Long userId) {
        Scope scope = preferredScope(def, addr, userId);
        clearAtScope(def, scope);
    }

    public void clearAtScope(StateDefinition<?> def, Scope scope) {
        if (scope == null) throw new IllegalArgumentException("scope is null");
        if (!def.allowedScopes().contains(scope.type())) {
            throw new IllegalArgumentException("scope " + scope.type() + " is not allowed for state " + def.name());
        }
        store.delete(scope, def.namespace(), def.name());
    }

}
