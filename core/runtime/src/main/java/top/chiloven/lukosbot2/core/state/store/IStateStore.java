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
