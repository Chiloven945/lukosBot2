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
package top.chiloven.lukosbot2.core.model;

import java.util.Map;

public record ServiceConfig(Map<String, String> values) {

    public String getOrDefault(String key, String def) {
        String v = values.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public boolean getBooleanOrDefault(String key, boolean def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }

    public int getIntOrDefault(String key, int def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Read {@code intervalMs} from config.
     *
     * @param def default interval in milliseconds
     *
     * @return interval in milliseconds (>= 1)
     */
    public long intervalMs(long def) {
        long v = getLongOrDefault("intervalMs", def);
        return Math.max(1L, v);
    }

    public long getLongOrDefault(String key, long def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

}
