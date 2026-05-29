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

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple sequential processor pipeline.
 *
 * <p>Each processor may contribute zero or more outbound messages. The pipeline concatenates
 * results in processor order.</p>
 */
@Service
public class PipelineProcessor {

    private final List<IProcessor> processors;

    public PipelineProcessor(List<IProcessor> processors) {
        this.processors = processors == null ? Collections.emptyList() : processors;
    }

    public List<OutboundMessage> handle(InboundMessage in) {
        if (processors.isEmpty()) return List.of();

        List<OutboundMessage> outs = new ArrayList<>();
        for (IProcessor p : processors) {
            if (p == null) continue;
            List<OutboundMessage> o = p.handle(in);
            if (o != null && !o.isEmpty()) outs.addAll(o);
        }
        return outs;
    }

}
