package top.chiloven.lukosbot2.commands.impl

import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.Base64Utils
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.getString
import top.chiloven.lukosbot2.util.JsonUtils.getStringByPath
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument
import java.io.IOException

/**
 * Query command for Minecraft: Java Edition players.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["player"],
    havingValue = "true",
    matchIfMissing = true
)
class PlayerCommand : IBotCommand {

    private val log = LogManager.getLogger(PlayerCommand::class.java)

    override fun name(): String = "player"

    override fun description(): String = "查询 Java 版玩家信息"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("查询玩家信息", UsageNode.oneOf(UsageNode.arg("name"), UsageNode.arg("uuid")))
            .syntax("根据用户名获取 UUID", UsageNode.arg("name"), UsageNode.lit("-u"))
            .syntax("根据 UUID 获取用户名", UsageNode.arg("uuid"), UsageNode.lit("-n"))
            .param("name", "玩家用户名（Java 版）")
            .param("uuid", "玩家 UUID（不带横线或带横线均可）")
            .option("-u", "强制按“用户名 → UUID”查询")
            .option("-n", "强制按“UUID → 用户名”查询")
            .example(
                "player jeb_",
                "player Notch -u"
            )
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                // /player <name|uuid>
                .then(
                    argument("data", StringArgumentType.word())
                        .executes { ctx ->
                            val src = ctx.source
                            val data = StringArgumentType.getString(ctx, "data")

                            try {
                                src.reply(getMcPlayerInfo(data).toString())
                                1
                            } catch (e: IOException) {
                                log.warn("Failed to fetch player info: {}", e.message, e)
                                src.reply("获取失败玩家信息失败，请检查你的输入或重试。")
                                0
                            }
                        }
                        // /player <data> <param>
                        .then(
                            argument("param", StringArgumentType.word())
                                .executes { ctx ->
                                    val src = ctx.source
                                    val data = StringArgumentType.getString(ctx, "data")
                                    val param = StringArgumentType.getString(ctx, "param")

                                    try {
                                        val result = when (param) {
                                            "-u" -> getUuidFromName(data)
                                            "-n" -> getNameFromUuid(data)
                                            else -> "不正确的参数：$param"
                                        }
                                        src.reply(result)
                                        1
                                    } catch (e: IOException) {
                                        log.warn("Failed to fetch player info: {}", e.message, e)
                                        src.reply("获取失败玩家信息失败，请检查你的输入或重试。")
                                        0
                                    }
                                }
                        )
                )
        )
    }

    companion object {
        val b64: Base64Utils = Base64Utils()

        /**
         * Get UUID from player's name.
         * Returns null if the player is not found or there's an error.
         */
        fun getUuidFromName(name: String): String? =
            HttpJson.getObject("https://api.mojang.com/users/profiles/minecraft/$name")
                .get("id")
                .asString

        /**
         * Get player's name from UUID.
         * Returns null if the player is not found or there's an error.
         */
        fun getNameFromUuid(uuid: String): String? =
            HttpJson.getObject("https://api.minecraftservices.com/minecraft/profile/lookup/$uuid")
                .get("name")
                .asString

        /**
         * Get the full player information (skin, cape, etc.) using either UUID or player name.
         * Throws RuntimeException if an error occurs while fetching or parsing the data.
         */
        fun getMcPlayerInfo(data: String): McPlayer {
            val uuid = if (data.length <= 16) getUuidFromName(data) else data
            val info: JsonObject =
                HttpJson.getObject("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            val value: JsonObject = b64.decodeToJsonObject(getStringByPath(info, "properties[0].value", ""))

            return McPlayer(
                name = getString(info, "name", ""),
                uuid = getString(info, "id", ""),
                skin = getStringByPath(value, "textures.SKIN.url", null),
                cape = getStringByPath(value, "textures.CAPE.url", null)
            )
        }

        data class McPlayer(
            val name: String?,
            val uuid: String?,
            val skin: String?,
            val cape: String?
        ) {
            override fun toString(): String = buildString {
                appendLine("玩家名：$name")
                appendLine("UUID：$uuid")
                skin?.let { appendLine("皮肤：$it") }
                cape?.let { appendLine("披风：$it") }
            }
        }
    }
}
