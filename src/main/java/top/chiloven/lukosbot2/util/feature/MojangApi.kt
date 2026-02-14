package top.chiloven.lukosbot2.util.feature

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.Base64Utils
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.getString
import top.chiloven.lukosbot2.util.JsonUtils.getStringByPath
import java.io.IOException

object MojangApi {
    val hj: HttpJson = HttpJson.getHttpJson()
    val b64: Base64Utils = Base64Utils()

    /**
     * Get UUID from player's name.
     * Returns null if the player is not found or there's an error.
     */
    @JvmStatic
    fun getUuidFromName(name: String): String? = try {
        hj.getObject("https://api.mojang.com/users/profiles/minecraft/$name")
            .get("id")
            .asString
    } catch (_: IOException) {
        null
    }

    /**
     * Get player's name from UUID.
     * Returns null if the player is not found or there's an error.
     */
    @JvmStatic
    fun getNameFromUuid(uuid: String): String? = try {
        hj.getObject("https://api.minecraftservices.com/minecraft/profile/lookup/$uuid")
            .get("name")
            .asString
    } catch (e: IOException) {
        null
    }

    /**
     * Get the full player information (skin, cape, etc.) using either UUID or player name.
     * Throws RuntimeException if an error occurs while fetching or parsing the data.
     */
    @JvmStatic
    fun getMcPlayerInfo(data: String): McPlayer {
        val uuid = if (data.length <= 16) getUuidFromName(data) else data

        try {
            val info: JsonObject = hj.getObject("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            val value: JsonObject = b64.decodeToJsonObject(getStringByPath(info, "properties[0].value", ""))

            return McPlayer(
                name = getString(info, "name", ""),
                uuid = getString(info, "id", ""),
                skin = getStringByPath(value, "textures.SKIN.url", null),
                cape = getStringByPath(value, "textures.CAPE.url", null)
            )
        } catch (e: IOException) {
            throw RuntimeException("Failed to fetch player info for $data", e)
        }
    }

    @JvmRecord
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
