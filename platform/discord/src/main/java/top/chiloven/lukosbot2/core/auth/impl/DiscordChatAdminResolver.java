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
package top.chiloven.lukosbot2.core.auth.impl;

import top.chiloven.lukosbot2.core.auth.IChatAdminResolver;
import top.chiloven.lukosbot2.core.command.bot.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

public class DiscordChatAdminResolver implements IChatAdminResolver {

    @Override
    public boolean supports(ChatPlatform platform) {
        return platform == ChatPlatform.DISCORD;
    }

    @Override
    public boolean isChatAdmin(CommandSource src) {
        return src.ext("discord.chatAdmin") instanceof Boolean b1
                ? b1
                : src.ext("discord.guildAdmin") instanceof Boolean b2 && b2;
    }

}
