package top.chiloven.lukosbot2.core.state.store;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.ScopeType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation for {@link IStateStore}.
 *
 * <p>Compatible with H2 out-of-the-box, and intentionally avoids vendor-specific UPSERT syntax
 * (uses update-then-insert pattern) so it can be reused for other databases later.</p>
 *
 * @author Chiloven945
 */
@Service
public class JdbcStateStore implements IStateStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStateStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> getJson(Scope scope, String namespace, String key) {
        String sql = """
                SELECT v_json
                FROM bot_state
                WHERE scope_type=:st
                  AND scope_id=:sid
                  AND namespace=:ns
                  AND k=:k
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """;
        var params = Map.of(
                "st", scope.type().name(),
                "sid", scope.id(),
                "ns", namespace,
                "k", key
        );
        var list = jdbc.query(sql, params, (rs, _) -> rs.getString(1));
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }

    @Override
    public Map<String, String> getNamespaceJson(Scope scope, String namespace) {
        String sql = """
                SELECT k, v_json
                FROM bot_state
                WHERE scope_type=:st
                  AND scope_id=:sid
                  AND namespace=:ns
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                ORDER BY k
                """;
        var params = Map.of(
                "st", scope.type().name(),
                "sid", scope.id(),
                "ns", namespace
        );
        return jdbc.query(sql, params, rs -> {
            Map<String, String> out = new LinkedHashMap<>();
            while (rs.next()) {
                out.put(rs.getString("k"), rs.getString("v_json"));
            }
            return out;
        });
    }

    @Override
    public void upsertJson(Scope scope, String namespace, String key, String json, Instant expiresAtOrNull) {
        Map<String, Object> p = new HashMap<>();
        p.put("st", scope.type().name());
        p.put("sid", scope.id());
        p.put("ns", namespace);
        p.put("k", key);
        p.put("v", json);
        p.put("exp", expiresAtOrNull == null ? null : Timestamp.from(expiresAtOrNull));

        int updated = jdbc.update("""
                UPDATE bot_state
                   SET v_json=:v,
                       expires_at=:exp,
                       updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE scope_type=:st
                   AND scope_id=:sid
                   AND namespace=:ns
                   AND k=:k
                """, p);

        if (updated != 0) return;

        try {
            jdbc.update("""
                    INSERT INTO bot_state(
                        scope_type, scope_id, namespace, k,
                        v_json, expires_at, created_at, updated_at, version
                    ) VALUES (
                        :st, :sid, :ns, :k,
                        :v, :exp, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """, p);
        } catch (DuplicateKeyException e) {
            // concurrent insert: retry update
            jdbc.update("""
                    UPDATE bot_state
                       SET v_json=:v,
                           expires_at=:exp,
                           updated_at=CURRENT_TIMESTAMP,
                           version=version+1
                     WHERE scope_type=:st
                       AND scope_id=:sid
                       AND namespace=:ns
                       AND k=:k
                    """, p);
        }
    }

    @Override
    public void delete(Scope scope, String namespace, String key) {
        String sql = """
                DELETE FROM bot_state
                WHERE scope_type=:st
                  AND scope_id=:sid
                  AND namespace=:ns
                  AND k=:k
                """;
        var params = Map.of(
                "st", scope.type().name(),
                "sid", scope.id(),
                "ns", namespace,
                "k", key
        );
        jdbc.update(sql, params);
    }

    @Override
    public Map<String, Map<String, String>> scanByScopeTypeAndNamespace(ScopeType type, String namespace) {
        String sql = """
                SELECT scope_id, k, v_json
                FROM bot_state
                WHERE scope_type=:st
                  AND namespace=:ns
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                ORDER BY scope_id, k
                """;
        var params = Map.of(
                "st", type.name(),
                "ns", namespace
        );

        return jdbc.query(sql, params, rs -> {
            Map<String, Map<String, String>> out = new LinkedHashMap<>();
            while (rs.next()) {
                String sid = rs.getString("scope_id");
                String k = rs.getString("k");
                String v = rs.getString("v_json");
                out.computeIfAbsent(sid, _ -> new LinkedHashMap<>()).put(k, v);
            }
            return out;
        });
    }

}
