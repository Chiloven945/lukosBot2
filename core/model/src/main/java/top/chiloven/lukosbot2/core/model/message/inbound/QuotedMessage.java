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

import java.util.Collections;
import java.util.List;

/**
 * Lightweight snapshot of a quoted / replied-to inbound message.
 */
public record QuotedMessage(
        String messageId,
        Long senderId,
        List<InPart> parts
) {

    public QuotedMessage {
        if (parts == null) parts = List.of();
    }

    public List<InPart> partsSafe() {
        return parts == null ? Collections.emptyList() : parts;
    }

}
