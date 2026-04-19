package top.chiloven.lukosbot2.commands.impl.motd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

@JsonIgnoreProperties(ignoreUnknown = true)
data class McSrvStatusResponse(
    val online: Boolean = false,
    val ip: String? = null,
    val port: Int? = null,
    val hostname: String? = null,
    val version: String? = null,
    val protocol: ProtocolInfo? = null,
    val icon: String? = null,
    val software: String? = null,
    val eulaBlocked: Boolean? = null,
    val motd: MotdInfo? = null,
    val players: PlayersInfo? = null,
    val debug: DebugInfo? = null,
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProtocolInfo(
        val version: Int? = null,
        val name: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MotdInfo(
        val raw: List<String>? = null,
        val clean: List<String>? = null,
        val html: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PlayersInfo(
        val online: Int? = null,
        val max: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DebugInfo(
        val ping: Boolean? = null,
        val query: Boolean? = null,
        val bedrock: Boolean? = null,
        val srv: Boolean? = null,
        val cachehit: Boolean? = null,
    )

    companion object {

        fun fromJsonObject(obj: ObjectNode): McSrvStatusResponse =
            JsonUtils.snakeTreeToValue(obj, McSrvStatusResponse::class.java)

    }

}
