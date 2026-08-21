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
package top.chiloven.lukosbot2.core.auth;

import top.chiloven.lukosbot2.core.command.bot.CommandSource;

import java.util.List;

public class AuthorizationService {

    private final BotAdminService botAdminService;
    private final List<IChatAdminResolver> chatAdminResolvers;

    public AuthorizationService(
            BotAdminService botAdminService,
            List<IChatAdminResolver> chatAdminResolvers
    ) {
        this.botAdminService = botAdminService;
        this.chatAdminResolvers = chatAdminResolvers;
    }

    public boolean ensureBotAdmin(CommandSource src, String action) {
        var ctx = inspect(src);
        if (!ctx.canManageGlobal()) {
            src.reply(
                    "权限不足：只有机器人管理员可以%s。发送 `/admin me` 可查看当前身份。".formatted(
                            action)
            );
            return false;
        }
        return true;
    }

    public AuthContext inspect(CommandSource src) {
        var platform = src.platform();
        var userId = src.userIdOrNull();

        var botAdmin = botAdminService.isBotAdmin(platform, userId);
        var chatAdmin = botAdmin || resolveChatAdmin(src);

        return new AuthContext(botAdmin, chatAdmin);
    }

    private boolean resolveChatAdmin(CommandSource src) {
        if (!src.isGroup()) {
            return false;
        }

        var platform = src.platform();
        if (platform == null) {
            return false;
        }

        return chatAdminResolvers.stream()
                .anyMatch(resolver ->
                        resolver.supports(platform) && resolver.isChatAdmin(src)
                );
    }

    public boolean ensureChatManager(CommandSource src, String action) {
        var ctx = inspect(src);
        if (!ctx.canManageChat()) {
            src.reply(
                    "权限不足：只有当前聊天管理员或机器人管理员可以%s。发送 `/admin me` 可查看当前身份。"
                            .formatted(action)
            );
            return false;
        }
        return true;
    }

}
