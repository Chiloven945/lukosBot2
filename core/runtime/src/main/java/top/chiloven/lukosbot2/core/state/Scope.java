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
package top.chiloven.lukosbot2.core.state;

import org.jspecify.annotations.NonNull;

import top.chiloven.lukosbot2.core.model.message.Address;
import top.chiloven.lukosbot2.platform.ChatPlatform;

/**
 * A scope identifies where a piece of state is stored.
 *
 * <p>We store different kinds of states (prefs, services, etc.) in a single table, partitioned by:
 * (scopeType, scopeId, namespace, key) -> json</p>
 */
public record Scope(
        ScopeType type,
        String id
) {

    public static @NonNull Scope global() {
        return new Scope(ScopeType.GLOBAL, "_");
    }

    public static @NonNull Scope user(@NonNull ChatPlatform platform, long userId) {
        return new Scope(ScopeType.USER, platform.name() + ":" + userId);
    }

    public static @NonNull Scope chat(@NonNull Address addr) {
        return new Scope(ScopeType.CHAT, chatKey(addr));
    }

    /**
     * Chat key format: PLATFORM:(g|p):chatId
     */
    public static @NonNull String chatKey(@NonNull Address addr) {
        return addr.platform().name() + ":" + (addr.group() ? "g" : "p") + ":" + addr.chatId();
    }

}
