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
package top.chiloven.lukosbot2.lifecycle.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.core.MessageSenderHub;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.discord.DiscordReceiver;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "lukos.discord", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class DiscordLifecycle implements IPlatformAdapter {

    private final MessageDispatcher md;
    private final MessageSenderHub msh;
    private final AppProperties props;
    private final ProxyConfigProp proxyConfigProp;

    private final BaseCloseable closeable = new BaseCloseable();
    private volatile boolean running = false;

    @Override
    public void start() {
        if (running) return;
        try {
            closeable.addAll(start(md, msh));
            running = true;
            log.info("[{}] started (prefix='{}')", name(), props.getPrefix());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start " + name(), e);
        }
    }

    @Override
    public List<AutoCloseable> start(
            @NonNull MessageDispatcher md,
            @NonNull MessageSenderHub msh
    ) throws Exception {
        var dc = new DiscordReceiver(props.getDiscord().getToken(), proxyConfigProp);
        dc.bind(md::receive);
        dc.start();
        msh.register(ChatPlatform.DISCORD, dc.sender());

        log.info("Discord ready");
        return List.of(dc);
    }

    @Override
    public String name() {
        return "Discord";
    }

    @Override
    public void stop() {
        try {
            closeable.close();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop(@NonNull Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public int getPhase() {
        return 0;
    }

}
