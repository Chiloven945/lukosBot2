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
package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.service.ServiceManager

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["service"],
    havingValue = "true",
    matchIfMissing = true
)
class ServiceCommand(
    private val services: ServiceManager,
    private val authz: AuthorizationService
) : IBotCommand {

    private val commandDefinition = botCommand("service") {
        description = "管理机器人服务"

        literal("list") {
            execute {
                source.reply(renderChatList(source))
            }
        }

        literal("global") {
            literal("list") {
                execute {
                    listGlobal(source)
                }
            }
            argv {
                positional("service", ArgType.StringType) {
                    required = true
                }
                positional("key", ArgType.StringType) {
                    required = false
                }
                positional("value", ArgType.StringType) {
                    required = false
                    greedy = true
                }
                execute { args ->
                    val svc = args.get<String>("service")
                    val key = args.getOrNull<String>("key")
                    val value = args.getOrNull<String>("value")
                    when {
                        key == null -> toggleGlobal(source, svc)
                        value == null -> getGlobal(source, svc, key)
                        else -> setGlobal(source, svc, key, value)
                    }
                }
            }
        }

        argv {
            positional("service", ArgType.StringType) {
                required = true
            }
            positional("key", ArgType.StringType) {
                required = false
            }
            positional("value", ArgType.StringType) {
                required = false
                greedy = true
            }
            execute { args ->
                val svc = args.get<String>("service")
                val key = args.getOrNull<String>("key")
                val value = args.getOrNull<String>("value")
                when {
                    key == null -> toggleChat(source, svc)
                    value == null -> getChat(source, svc, key)
                    else -> setChat(source, svc, key, value)
                }
            }
        }

        example(
            "service list",
            "service weather",
            "service weather intervalMs 60000",
            "service global list"
        )
    }

    override fun definition() = commandDefinition

    private fun renderChatList(src: CommandSource): String {
        val st = services.snapshotStates(src.addr())
        return services.registry.all()
            .filter { services.isAllowed(it.name()) }
            .sortedBy { it.name() }
            .joinToString("\n", "当前聊天的服务：\n") { s ->
                val ss = st[s.name()]
                val enabled = ss != null && ss.isEnabled
                "- ${s.name()} [${if (enabled) "已启用" else "已停用"}]（${s.description() ?: ""}）"
            }
    }

    private fun listGlobal(src: CommandSource) {
        if (!authz.ensureBotAdmin(src, "查看全局服务配置")) return

        val st = services.snapshotDefaultStates()
        val text = services.registry.all()
            .filter { services.isAllowed(it.name()) }
            .sortedBy { it.name() }
            .joinToString("\n", "全局默认服务：\n") { s ->
                val ss = st[s.name()]
                val enabled = ss != null && ss.isEnabled
                "- ${s.name()} [${if (enabled) "已启用" else "已停用"}]（${s.description() ?: ""}）"
            }
        src.reply(text)
    }

    private fun toggleChat(src: CommandSource, name: String) {
        if (!authz.ensureChatManager(src, "修改当前聊天服务开关")) return

        if (!services.isAllowed(name)) {
            src.reply("当前不允许使用此服务：$name")
            return
        }
        if (services.registry.find(name).isEmpty) {
            src.reply("未找到服务：$name")
            return
        }

        val st = services.stateOf(src.addr(), name)
        val nowEnabled = st == null || !st.isEnabled
        services.setEnabled(src.addr(), name, nowEnabled)
        src.reply("服务\"$name\"已在当前聊天中${if (nowEnabled) "启用" else "停用"}。")
    }

    private fun getChat(src: CommandSource, svc: String, key: String) {
        if (!services.isAllowed(svc)) {
            src.reply("当前不允许使用此服务：$svc")
            return
        }
        if (services.registry.find(svc).isEmpty) {
            src.reply("未找到服务：$svc")
            return
        }

        val st = services.stateOf(src.addr(), svc)
        if (st == null || st.config == null) {
            src.reply("当前聊天没有为服务\"$svc\"设置配置。")
            return
        }

        src.reply(st.config?.get(key) ?: "未设置")
    }

    private fun setChat(
        src: CommandSource,
        svc: String,
        key: String,
        value: String
    ) {
        if (!authz.ensureChatManager(src, "修改当前聊天服务配置")) return

        if (!services.isAllowed(svc)) {
            src.reply("当前不允许使用此服务：$svc")
            return
        }
        if (services.registry.find(svc).isEmpty) {
            src.reply("未找到服务：$svc")
            return
        }

        services.setConfigValue(src.addr(), svc, key, value)
        src.reply("已更新当前聊天的服务配置：$svc.$key = $value")
    }

    private fun toggleGlobal(src: CommandSource, name: String) {
        if (!authz.ensureBotAdmin(src, "修改全局服务开关")) return

        if (!services.isAllowed(name)) {
            src.reply("当前不允许使用此服务：$name")
            return
        }
        if (services.registry.find(name).isEmpty) {
            src.reply("未找到服务：$name")
            return
        }

        val st = services.defaultStateOf(name)
        val nowEnabled = st == null || !st.isEnabled
        services.setDefaultEnabled(name, nowEnabled)
        src.reply("服务\"$name\"的全局默认状态已${if (nowEnabled) "启用" else "停用"}。")
    }

    private fun getGlobal(src: CommandSource, svc: String, key: String) {
        if (!authz.ensureBotAdmin(src, "查看全局服务配置")) return

        if (!services.isAllowed(svc)) {
            src.reply("当前不允许使用此服务：$svc")
            return
        }
        if (services.registry.find(svc).isEmpty) {
            src.reply("未找到服务：$svc")
            return
        }

        val st = services.defaultStateOf(svc)
        if (st == null || st.config == null) {
            src.reply("服务\"$svc\"没有全局默认配置。")
            return
        }

        src.reply(st.config?.get(key) ?: "未设置")
    }

    private fun setGlobal(
        src: CommandSource,
        svc: String,
        key: String,
        value: String
    ) {
        if (!authz.ensureBotAdmin(src, "修改全局服务配置")) return

        if (!services.isAllowed(svc)) {
            src.reply("当前不允许使用此服务：$svc")
            return
        }
        if (services.registry.find(svc).isEmpty) {
            src.reply("未找到服务：$svc")
            return
        }

        services.setDefaultConfigValue(svc, key, value)
        src.reply("已更新全局默认服务配置：$svc.$key = $value")
    }

}
