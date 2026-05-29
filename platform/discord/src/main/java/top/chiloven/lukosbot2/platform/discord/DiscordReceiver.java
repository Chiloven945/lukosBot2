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
package top.chiloven.lukosbot2.platform.discord;

import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.IReceiver;
import top.chiloven.lukosbot2.platform.ISender;

import java.util.function.Consumer;

public final class DiscordReceiver implements IReceiver {

    private final DiscordStack stack;
    private Consumer<InboundMessage> sink = _ -> {
    };

    public DiscordReceiver(String token, ProxyConfigProp proxyConfigProp) {
        this.stack = new DiscordStack(token, proxyConfigProp);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.DISCORD;
    }

    @Override
    public void bind(Consumer<InboundMessage> sink) {
        this.sink = (sink != null) ? sink : _ -> {
        };
        stack.setSink(this.sink);
    }

    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.setSink(sink);
    }

    @Override
    public void stop() {
        stack.close();
    }

    public ISender sender() throws Exception {
        start(); // idempotent
        return new DiscordSender(stack);
    }

}
