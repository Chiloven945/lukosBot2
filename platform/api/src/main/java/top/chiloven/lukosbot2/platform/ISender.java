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
package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage;

/**
 * Platform-specific sender for outbound rich messages.
 *
 * <p>Implementations are responsible for translating {@link OutboundMessage} into
 * native platform API calls (Telegram, Discord, ...), including handling of mixed content (text + image/file)
 * and any platform limits.</p>
 */
public interface ISender {

    /**
     * Send an outbound message.
     *
     * <p>This method should be thread-safe. Ordering guarantees (per chat) are handled
     * by {@code MessageSenderHub}.</p>
     *
     * @param out outbound message (must not be null)
     */
    void send(OutboundMessage out);

}
