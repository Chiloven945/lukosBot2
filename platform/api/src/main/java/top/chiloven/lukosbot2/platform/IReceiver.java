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

import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;

import java.util.function.Consumer;

/**
 * Receiver interface: responsible for receiving messages from a specific platform and passing them to the message
 * handler.
 */
public interface IReceiver extends AutoCloseable {

    ChatPlatform platform();

    /**
     * Bind message handler.
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    void bind(Consumer<InboundMessage> sink);

    /**
     * Start the receiver.
     *
     * @throws Exception throw exception if start failed
     */
    void start() throws Exception;

    /**
     * Close the receiver, equivalent to stop().
     *
     * @throws Exception throw exception if stop failed
     */
    @Override
    default void close() throws Exception {
        stop();
    }

    /**
     * Stop the receiver.
     *
     * @throws Exception throw exception if stop failed
     */
    void stop() throws Exception;

}
