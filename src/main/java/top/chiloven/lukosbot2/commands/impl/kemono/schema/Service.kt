package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URI
import java.util.*

/**
 * Services supported by kemono.
 */
enum class Service(
    @get:JsonValue
    val id: String,
    val siteName: String,
    val abbr: String,
) {

    PATREON("patreon", "Patreon", "p"),
    FANBOX("fanbox", "Pixiv Fanbox", "f"),
    DISCORD("discord", "Discord", "d"),
    FANTIA("fantia", "Fantia", "fa"),
    AFDIAN("afdian", "Afdian", "afd"),
    BOOSTY("boosty", "Boosty", "b"),
    GUMROAD("gumroad", "Gumroad", "g"),
    SUBSCRIBE_STAR("subscribestar", "SubscribeStar", "ss"),
    DL_SITE("dlsite", "DLSite", "dl"),
    UNKNOWN("unknown", "Unknown", "Unknown");

    override fun toString(): String = id

    data class ServiceAndPostId(
        val service: Service,
        val servicePostId: String
    )

    companion object {

        private val byAbbr = entries.associateBy { it.abbr }
        private val byId = entries.associateBy { it.id }

        @JvmStatic
        fun getService(code: String): Service {
            val normalized = code.trim().lowercase(Locale.ROOT)
            return byAbbr[normalized] ?: byId[normalized]
            ?: throw IllegalArgumentException("未知平台：$code")
        }

        @JvmStatic
        @JsonCreator
        fun fromJson(code: String): Service {
            val normalized = code.trim().lowercase(Locale.ROOT)
            return byAbbr[normalized] ?: byId[normalized] ?: UNKNOWN
        }

        fun parseServicePostUrl(uri: URI): ServiceAndPostId {
            val host = (uri.host ?: "").lowercase(Locale.ROOT)
            val raw = uri.toString()

            fun requireId(service: Service, regex: Regex, hint: String): ServiceAndPostId {
                val id = regex.find(raw)?.groupValues?.getOrNull(1)
                    ?: throw IllegalArgumentException("无法从链接中解析 $hint：$uri")
                return ServiceAndPostId(service, id)
            }

            return when {
                "patreon.com" in host -> requireId(
                    PATREON,
                    Regex("(?:/posts/[^/?#]*-|/posts/)(\\d+)(?:[/?#].*)?$"),
                    "Patreon 帖子 ID"
                )

                "fanbox.cc" in host -> requireId(
                    FANBOX,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "Fanbox 帖子 ID"
                )

                "discord.com" in host -> throw IllegalArgumentException(
                    "暂不支持 Discord 内容。"
                )

                "fantia.jp" in host -> requireId(
                    FANTIA,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "Fantia 帖子 ID"
                )

                "afdian.net" in host -> requireId(
                    AFDIAN,
                    Regex("/p/([A-Za-z0-9]+)(?:[/?#].*)?$"),
                    "Afdian 帖子 ID"
                )

                "boosty.to" in host -> requireId(
                    BOOSTY,
                    Regex("/posts/([A-Za-z0-9-]+)(?:[/?#].*)?$"),
                    "Boosty 帖子 ID"
                )

                "subscribestar.com" in host -> requireId(
                    SUBSCRIBE_STAR,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "SubscribeStar 帖子 ID"
                )

                "dlsite.com" in host -> requireId(
                    DL_SITE,
                    Regex("/product_id/([A-Za-z0-9_]+)\\.html(?:[?#].*)?$"),
                    "DLsite 商品 ID"
                )

                "gumroad.com" in host -> throw IllegalArgumentException(
                    "暂不支持从 Gumroad 原站链接解析帖子，请改用 kemono 链接，或提供平台、创作者 ID 和帖子 ID。"
                )

                else -> throw IllegalArgumentException("不支持的平台链接：$uri")
            }
        }

    }

}
