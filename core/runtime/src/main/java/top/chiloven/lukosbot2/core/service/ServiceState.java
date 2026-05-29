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
package top.chiloven.lukosbot2.core.service;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable state of one bot service at one scope.
 *
 * <p>The service manager stores one {@code ServiceState} per service name and scope. The same structure
 * is used for global defaults and chat-local overrides.</p>
 *
 * <p>The model intentionally stays simple:</p>
 * <ul>
 *   <li>{@link #isEnabled()} controls whether the service is active</li>
 *   <li>{@link #getConfig()} stores string-based key/value settings for the service</li>
 * </ul>
 *
 * <p>{@link LinkedHashMap} is used to keep insertion order stable for display, debugging, and persistence.</p>
 */
@Getter
public final class ServiceState {

    /**
     * Whether the service is currently enabled at this scope.
     */
    @Setter
    private boolean enabled;

    /**
     * Service-specific configuration values persisted as string pairs.
     */
    private Map<String, String> config = new LinkedHashMap<>();

    /**
     * Creates an empty disabled service state.
     */
    public ServiceState() {
    }

    /**
     * Creates a state with an explicit enabled flag and config copy.
     *
     * @param enabled whether the service is enabled.
     * @param config  service configuration map; copied defensively when non-null.
     */
    public ServiceState(boolean enabled, Map<String, String> config) {
        this.enabled = enabled;
        this.config = (config == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }

    /**
     * Replaces the configuration map.
     *
     * <p>A null input resets the map to an empty {@link LinkedHashMap}. Non-null maps are accepted as-is
     * to preserve the caller's chosen implementation when needed.</p>
     *
     * @param config new configuration map.
     */
    public void setConfig(Map<String, String> config) {
        this.config = (config == null) ? new LinkedHashMap<>() : config;
    }

}
