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
package top.chiloven.lukosbot2.platform

import kotlinx.coroutines.flow.Flow
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage

/**
 * Receiver interface: responsible for receiving messages from a specific platform and exposing them as a
 * [Flow] of inbound messages.
 */
interface IReceiver {

    val platform: ChatPlatform

    /**
     * Inbound message stream. A collector (for example the platform lifecycle) forwards each message to
     * `MessageDispatcher.receive`. The stream completes when the receiver is stopped.
     */
    val messages: Flow<InboundMessage>

    /**
     * Start the receiver.
     *
     * @throws Exception throw exception if start failed
     */
    suspend fun start()

    /**
     * Stop the receiver.
     *
     * @throws Exception throw exception if stop failed
     */
    suspend fun stop()

}
