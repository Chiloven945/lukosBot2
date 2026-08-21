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
package top.chiloven.lukosbot2.core

import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage

/**
 * A simple sequential processor pipeline.
 *
 * <p>Each processor may contribute zero or more outbound messages. The pipeline concatenates
 * results in processor order.</p>
 */
class PipelineProcessor(
    processors: List<IProcessor>?
) {

    private val processors: List<IProcessor> = processors.orEmpty()

    suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
        return if (processors.isEmpty()) {
            emptyList()
        } else {
            processors.flatMap { it.handle(inbound) }
        }

    }

}
