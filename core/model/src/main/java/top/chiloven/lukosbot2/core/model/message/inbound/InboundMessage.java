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

import top.chiloven.lukosbot2.core.model.message.Address;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * New inbound message model.
 *
 * <p>This model is designed to carry richer information than the legacy {@code MessageIn},
 * including non-text parts (image/file), metadata, and platform-specific extensions.</p>
 */
public record InboundMessage(
        Address addr,
        Sender sender,
        Chat chat,
        MessageMeta meta,
        List<InPart> parts,
        Map<String, Object> ext,
        QuotedMessage quoted
) {

    public InboundMessage(
            Address addr,
            Sender sender,
            Chat chat,
            MessageMeta meta,
            List<InPart> parts,
            Map<String, Object> ext
    ) {
        this(addr, sender, chat, meta, parts, ext, null);
    }

    public InboundMessage {
        if (sender == null) sender = Sender.unknown();
        if (chat == null) chat = new Chat(addr, null);
        if (meta == null) meta = MessageMeta.empty();
        if (parts == null) parts = List.of();
        if (ext == null) ext = Map.of();
    }

    public List<InPart> partsSafe() {
        return parts == null ? Collections.emptyList() : parts;
    }

} 
