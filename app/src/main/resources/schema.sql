/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
