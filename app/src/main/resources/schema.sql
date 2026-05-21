CREATE TABLE IF NOT EXISTS bot_state
(
    scope_type VARCHAR(16)  NOT NULL,
    scope_id   VARCHAR(128) NOT NULL,
    namespace  VARCHAR(64)  NOT NULL,
    k          VARCHAR(128) NOT NULL,
    v_json     CLOB         NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    expires_at TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_type, scope_id, namespace, k)
);

CREATE INDEX IF NOT EXISTS idx_bot_state_ns ON bot_state (namespace);
CREATE INDEX IF NOT EXISTS idx_bot_state_exp ON bot_state (expires_at);
CREATE INDEX IF NOT EXISTS idx_bot_state_scope ON bot_state (scope_type, scope_id);
