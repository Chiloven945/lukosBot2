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
package top.chiloven.lukosbot2.core;

import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage;

import java.util.List;

/**
 * A message processor that consumes an inbound message and produces zero or more outbound messages.
 *
 * <p>Processors are typically chained by {@link PipelineProcessor}.</p>
 */
public interface IProcessor {

    /**
     * Handle an inbound message.
     *
     * @param in inbound message
     *
     * @return outbound messages to be sent (may be empty, never null)
     */
    List<OutboundMessage> handle(InboundMessage in);

}
