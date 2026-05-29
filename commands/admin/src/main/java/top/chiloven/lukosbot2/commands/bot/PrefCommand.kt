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
import top.chiloven.lukosbot2.core.state.Scope
import top.chiloven.lukosbot2.core.state.ScopeType
import top.chiloven.lukosbot2.core.state.StateRegistry
import top.chiloven.lukosbot2.core.state.StateService
import top.chiloven.lukosbot2.core.state.definition.IStateDefinition
import java.util.*

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["pref"],
    havingValue = "true",
    matchIfMissing = true
)
class PrefCommand(
    private val registry: StateRegistry,
    private val states: StateService,
    private val authz: AuthorizationService
) : IBotCommand {

    private val commandDefinition = botCommand("pref") {
        description = "查看和管理偏好设置"

        literal("list") {
            execute {
                source.reply(renderList())
            }
        }

        literal("get") {
            argv {
                positional("scope", ArgType.StringType) {
                    required = false
                }
                positional("state", ArgType.StringType) {
                    required = true
                }
                execute { args ->
                    val scopeRaw = args.getOrNull<String>("scope")
                    val stateName = args.get<String>("state")
                    if (scopeRaw != null) getExplicit(source, scopeRaw, stateName)
                    else getResolved(source, stateName)
                }
            }
        }

        literal("set") {
            argv {
                positional("scope", ArgType.StringType) {
                    required = true
                }
                positional("state", ArgType.StringType) {
                    required = true
                }
                positional("value", ArgType.StringType) {
                    required = true
                    greedy = true
                }
                execute { args ->
                    setExplicit(
                        source,
                        args.get("scope"),
                        args.get("state"),
                        args.get("value")
                    )
                }
            }
        }

        literal("clear") {
            argv {
                positional("scope", ArgType.StringType) {
                    required = true
                }
                positional("state", ArgType.StringType) {
                    required = true
                }
                execute { args ->
                    clearExplicit(
                        source,
                        args.get("scope"),
                        args.get("state")
                    )
                }
            }
        }

        execute {
            source.reply(renderList())
        }
        example(
            "pref list",
            "pref get lang",
            "pref set user lang en-us",
            "pref clear global notifyMode"
        )
    }

    override fun definition() = commandDefinition

    private fun displayScope(type: ScopeType) = when (type) {
        ScopeType.USER -> "本人"
        ScopeType.CHAT -> "当前聊天"
        ScopeType.GLOBAL -> "全局"
    }

    private fun renderList(): String {
        val sb = StringBuilder("可用的配置项：\n")
        val defs = registry.all().sortedBy { it.name() }
        if (defs.isEmpty()) {
            sb.append("暂无可用配置项。")
            return sb.toString()
        }

        for (d in defs) {
            val dName = d.name()
            val dDesc = d.description()
            val scopes = d.allowedScopes().map { displayScope(it) }.sorted().joinToString("/")
            val order = d.resolveOrder().joinToString(" -> ") { displayScope(it) }
            sb.append("- ${dName}：${dDesc}；可用范围：${scopes}；生效优先级：${order}")
            val sv = d.suggestValues()
            if (sv != null && sv.isNotEmpty()) sb.append("；可选值：" + sv.joinToString("、"))
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getResolved(src: CommandSource, stateName: String) {
        val opt = registry.find(stateName)
        if (opt.isEmpty) {
            src.reply("未找到配置项：$stateName")
            return
        }

        val def = opt.get() as IStateDefinition<Any>
        val v = states.resolve(def, src.addr(), src.userIdOrNull())
        src.reply("${def.name()} 当前生效值：${def.format(v)}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun getExplicit(
        src: CommandSource,
        scopeRaw: String,
        stateName: String
    ) {
        val opt = registry.find(stateName)
        if (opt.isEmpty) {
            src.reply("未找到配置项：$stateName")
            return
        }

        val def = opt.get() as IStateDefinition<Any>
        try {
            val scope = parseScope(scopeRaw, src, def)
            val v = states.getAtScope(def, scope)
            val scopeName = displayScope(scope.type())
            val message = if (v == null) {
                "${def.name()} 在${scopeName}范围内未单独设置。"
            } else {
                "${def.name()} 在${scopeName}范围内的值：${def.format(v)}"
            }

            src.reply(message)
        } catch (e: IllegalArgumentException) {
            src.reply("读取失败：${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setExplicit(
        src: CommandSource,
        scopeRaw: String,
        stateName: String,
        rawValue: String
    ) {
        val opt = registry.find(stateName)
        if (opt.isEmpty) {
            src.reply("未找到配置项：$stateName")
            return
        }

        val def = opt.get() as IStateDefinition<Any>
        try {
            val scope = parseScope(scopeRaw, src, def)
            if (!ensureWriteAllowed(src, scope.type(), def.name())) return
            states.setAtScope(def, scope, rawValue)
            val v = states.getAtScope(def, scope)
            src.reply("已将 ${def.name()} 在${displayScope(scope.type())}范围内设置为：${def.format(v)}")
        } catch (e: IllegalArgumentException) {
            src.reply("设置失败：${e.message}")
        } catch (_: Exception) {
            src.reply("设置失败：请检查输入值。")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun clearExplicit(
        src: CommandSource,
        scopeRaw: String,
        stateName: String
    ) {
        val opt = registry.find(stateName)
        if (opt.isEmpty) {
            src.reply("未找到配置项：$stateName")
            return
        }

        val def = opt.get() as IStateDefinition<Any>
        try {
            val scope = parseScope(scopeRaw, src, def)
            if (!ensureWriteAllowed(src, scope.type(), def.name())) return
            states.clearAtScope(def, scope)
            src.reply("已清除 ${def.name()} 在${displayScope(scope.type())}范围内的设置。")
        } catch (e: IllegalArgumentException) {
            src.reply("清除失败：${e.message}")
        }
    }

    private fun parseScope(
        raw: String?,
        src: CommandSource,
        def: IStateDefinition<*>
    ): Scope {
        if (raw.isNullOrBlank()) throw IllegalArgumentException("配置范围不能为空")
        val type = when (raw.trim().lowercase(Locale.getDefault())) {
            "user" -> ScopeType.USER
            "chat" -> ScopeType.CHAT
            "global" -> ScopeType.GLOBAL
            else -> throw IllegalArgumentException("未知配置范围：$raw")
        }

        if (type !in def.allowedScopes()) {
            throw IllegalArgumentException("配置项不支持\"${displayScope(type)}\"范围。")
        }

        return when (type) {
            ScopeType.USER -> Scope.user(
                src.platform(),
                src.userIdOrNull() ?: throw IllegalArgumentException("无法使用 user 范围")
            )

            ScopeType.CHAT -> Scope.chat(src.addr())
            ScopeType.GLOBAL -> Scope.global()
        }
    }

    private fun ensureWriteAllowed(
        src: CommandSource,
        type: ScopeType,
        stateName: String
    ) = when (type) {
        ScopeType.USER -> true
        ScopeType.CHAT -> authz.ensureChatManager(src, "修改当前聊天配置（$stateName）")
        ScopeType.GLOBAL -> authz.ensureBotAdmin(src, "修改全局配置（$stateName）")
    }

}
