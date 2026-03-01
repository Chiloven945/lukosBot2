package top.chiloven.lukosbot2.core.state;

import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
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
    private final Gson gson = new Gson();

    public StateService(IStateStore store) {
        this.store = store;
    }

    public <T> T resolve(StateDefinition<T> def, Address addr, Long userId) {
        // CHAT override
        if (def.allowedScopes().contains(ScopeType.CHAT)) {
            var v = store.getJson(Scope.chat(addr), def.namespace(), def.name()).orElse(null);
            if (v != null) {
                try {
                    return gson.fromJson(v, def.type());
                } catch (Exception e) {
                    log.debug("Failed to parse state {}.{} at CHAT: {}", def.namespace(), def.name(), e.getMessage());
                }
            }
        }

        // USER override
        if (userId != null && def.allowedScopes().contains(ScopeType.USER)) {
            var v = store.getJson(Scope.user(addr.platform(), userId), def.namespace(), def.name()).orElse(null);
            if (v != null) {
                try {
                    return gson.fromJson(v, def.type());
                } catch (Exception e) {
                    log.debug("Failed to parse state {}.{} at USER: {}", def.namespace(), def.name(), e.getMessage());
                }
            }
        }

        // GLOBAL default
        if (def.allowedScopes().contains(ScopeType.GLOBAL)) {
            var v = store.getJson(Scope.global(), def.namespace(), def.name()).orElse(null);
            if (v != null) {
                try {
                    return gson.fromJson(v, def.type());
                } catch (Exception e) {
                    log.debug("Failed to parse state {}.{} at GLOBAL: {}", def.namespace(), def.name(), e.getMessage());
                }
            }
        }

        return def.defaultValue();
    }

    public <T> void set(StateDefinition<T> def, Address addr, Long userId, String rawValue) {
        if (rawValue == null) throw new IllegalArgumentException("value is null");
        T v = def.parse(rawValue);
        def.validate(v);

        Instant exp = def.ttl() == null ? null : Instant.now().plus(def.ttl());
        Scope scope = preferredScope(def, addr, userId);
        store.upsertJson(scope, def.namespace(), def.name(), gson.toJson(v), exp);
    }

    public Scope preferredScope(StateDefinition<?> def, Address addr, Long userId) {
        // 1) Try preferred scope
        Scope s = scopeByType(def.preferredScope(), def, addr, userId);
        if (s != null) return s;

        // 2) Fall back to USER -> CHAT -> GLOBAL
        s = scopeByType(ScopeType.USER, def, addr, userId);
        if (s != null) return s;
        s = scopeByType(ScopeType.CHAT, def, addr, userId);
        if (s != null) return s;
        s = scopeByType(ScopeType.GLOBAL, def, addr, userId);
        if (s != null) return s;

        // Should never happen for a well-defined StateDefinition
        return Scope.chat(addr);
    }

    private Scope scopeByType(ScopeType type, StateDefinition<?> def, Address addr, Long userId) {
        if (!def.allowedScopes().contains(type)) return null;
        return switch (type) {
            case CHAT -> Scope.chat(addr);
            case USER -> (userId == null) ? null : Scope.user(addr.platform(), userId);
            case GLOBAL -> Scope.global();
        };
    }

    public void clear(StateDefinition<?> def, Address addr, Long userId) {
        Scope scope = preferredScope(def, addr, userId);
        store.delete(scope, def.namespace(), def.name());
    }

}
