package top.chiloven.lukosbot2.commands.impl.kemono.schema

import java.net.URI
import java.util.*

/**
 * Services supported by kemono.
 */
enum class Service(
    val id: String,
    val siteName: String,
    val abbr: String,
) {

    PATREON("patreon", "Patreon", "p"),
    FANBOX("fanbox", "Pixiv Fanbox", "f"),

    //DISCORD("discord", "Discord", "d")
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

        fun getService(code: String): Service {
            val normalized = code.trim().lowercase(Locale.ROOT)
            return byAbbr[normalized] ?: byId[normalized]!!
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
                    "Patreon post id"
                )

                "fanbox.cc" in host -> requireId(
                    FANBOX,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "Fanbox post id"
                )

                "fantia.jp" in host -> requireId(
                    FANTIA,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "Fantia post id"
                )

                "afdian.net" in host -> requireId(
                    AFDIAN,
                    Regex("/p/([A-Za-z0-9]+)(?:[/?#].*)?$"),
                    "Afdian post id"
                )

                "boosty.to" in host -> requireId(
                    BOOSTY,
                    Regex("/posts/([A-Za-z0-9-]+)(?:[/?#].*)?$"),
                    "Boosty post id"
                )

                "subscribestar.com" in host -> requireId(
                    SUBSCRIBE_STAR,
                    Regex("/posts/(\\d+)(?:[/?#].*)?$"),
                    "SubscribeStar post id"
                )

                "dlsite.com" in host -> requireId(
                    DL_SITE,
                    Regex("/product_id/([A-Za-z0-9_]+)\\.html(?:[?#].*)?$"),
                    "DLsite product id"
                )

                "gumroad.com" in host -> throw IllegalArgumentException(
                    "暂不支持从 Gumroad 原站链接解析 post，请改用 kemono 链接或 `service + creator_id + post_id`。"
                )

                else -> throw IllegalArgumentException("不支持的平台链接：$uri")
            }
        }

    }

}
