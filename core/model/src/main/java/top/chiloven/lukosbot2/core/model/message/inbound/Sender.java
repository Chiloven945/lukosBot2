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
package top.chiloven.lukosbot2.core.model.message.inbound;

/**
 * Sender of an inbound message.
 *
 * <p>Most platforms provide a numeric user id; we keep it as {@link Long} and allow null
 * for system messages or unknown senders.</p>
 */
public record Sender(
        Long id,
        String username,
        String displayName,
        boolean bot
) {

    public Sender {
        // Normalize blanks to null for easier downstream checks
        if (username != null && username.isBlank()) username = null;
        if (displayName != null && displayName.isBlank()) displayName = null;
    }

    public static Sender unknown() {
        return new Sender(
                null,
                null,
                null,
                false
        );
    }

}
