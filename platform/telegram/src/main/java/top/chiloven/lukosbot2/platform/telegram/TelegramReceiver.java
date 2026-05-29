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
package top.chiloven.lukosbot2.platform.telegram;

import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.IReceiver;
import top.chiloven.lukosbot2.platform.ISender;

import java.util.function.Consumer;

public final class TelegramReceiver implements IReceiver {

    private final TelegramStack stack;
    private Consumer<InboundMessage> sink = _ -> {
    };

    public TelegramReceiver(String token, String username) {
        this.stack = new TelegramStack(token, username);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TELEGRAM;
    }

    @Override
    public void bind(Consumer<InboundMessage> sink) {
        this.sink = (sink != null) ? sink : _ -> {
        };
        if (stack.bot != null) stack.bot.setSink(this.sink);
    }

    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.bot.setSink(sink);
    }

    @Override
    public void stop() {
        try {
            stack.close();
        } catch (Exception _) {
        }
    }

    public ISender sender() throws Exception {
        start();
        return new TelegramSender(stack);
    }

}
