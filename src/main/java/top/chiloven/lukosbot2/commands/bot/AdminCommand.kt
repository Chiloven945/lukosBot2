package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.auth.AuthContext
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.auth.BotAdminService
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.platform.ChatPlatform

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["admin"],
    havingValue = "true",
    matchIfMissing = true
)
class AdminCommand(
    private val botAdmins: BotAdminService,
    private val authz: AuthorizationService
) : IBotCommand {

    private val commandDefinition = botCommand("admin") {
        description = "管理机器人管理员并查看当前身份"

        execute {
            this@AdminCommand.sendUsage(source)
        }

        literal("me") {
            description = "查看你的当前身份"
            execute { me(source) }
        }

        literal("list") {
            description = "查看机器人管理员列表"
            execute { list(source) }
        }

        literal("add") {
            description = "添加机器人管理员"
            argv {
                positional("platform", ArgType.StringType) {
                    required = true
                    description = "平台：telegram / discord / onebot"
                }
                positional("userId", ArgType.LongType) {
                    required = true
                    description = "平台用户 ID"
                }
                execute { args ->
                    add(
                        source,
                        args.get("platform"),
                        args.get("userId")
                    )
                }
            }
        }

        literal("remove") {
            description = "移除机器人管理员"
            argv {
                positional("platform", ArgType.StringType) {
                    required = true
                }
                positional("userId", ArgType.LongType) {
                    required = true
                }
                execute { args ->
                    remove(
                        source,
                        args.get("platform"),
                        args.get("userId")
                    )
                }
            }
        }

        syntax("查看你的当前身份", arg("me"))
        syntax(
            "添加机器人管理员",
            arg("add"),
            arg("platform"),
            arg("userId")
        )

        example(
            "admin me",
            "admin list",
            "admin add telegram 123456789",
            "admin remove discord 987654321"
        )
        note("list/add/remove 仅机器人管理员可用。")
    }

    override fun definition() = commandDefinition

    private fun me(src: CommandSource) {
        val auth: AuthContext = authz.inspect(src)
        src.reply(
            """
            平台：%s
            用户 ID：%s
            聊天 ID：%s
            群聊：%s
            机器人管理员：%s
            聊天管理员：%s
        """.trimIndent().format(
                src.platform().name,
                src.userIdOrNull() ?: "未知",
                src.chatId(),
                if (src.isGroup) "是" else "否",
                if (auth.botAdmin) "是" else "否",
                if (auth.chatAdmin) "是" else "否"
            )
        )
    }

    private fun list(src: CommandSource) {
        if (!authz.ensureBotAdmin(src, "查看机器人管理员列表")) return
        val admins: Map<ChatPlatform, Set<Long>> = botAdmins.listEffectiveAdmins()
        val text =
            admins.entries.sortedBy { it.key.name }.joinToString("\n", "当前有效的机器人管理员：\n") { (platform, ids) ->
                "- ${platform.name}: ${if (ids.isEmpty()) "无" else ids.sorted().joinToString(", ")}"
            }
        src.reply(text)
    }

    private fun add(
        src: CommandSource,
        platformRaw: String,
        userId: Long
    ) {
        if (!authz.ensureBotAdmin(src, "添加机器人管理员")) return
        val platform = parsePlatform(platformRaw, src) ?: return
        botAdmins.addDynamicAdmin(platform, userId)
        src.reply("已添加机器人管理员：%s:%d".format(platform.name, userId))
    }

    private fun remove(
        src: CommandSource,
        platformRaw: String,
        userId: Long
    ) {
        if (!authz.ensureBotAdmin(src, "移除机器人管理员")) return
        val platform = parsePlatform(platformRaw, src) ?: return
        botAdmins.removeDynamicAdmin(platform, userId)
        src.reply("已移除机器人管理员：%s:%d".format(platform.name, userId))
    }

    private fun parsePlatform(raw: String, src: CommandSource): ChatPlatform? = try {
        ChatPlatform.fromString(raw)
    } catch (_: IllegalArgumentException) {
        src.reply("不支持的平台：$raw")
        null
    }

}
