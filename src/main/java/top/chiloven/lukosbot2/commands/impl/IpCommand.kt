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
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument
import java.io.IOException

/**
 * Command for fetching IP address information.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["ip"],
    havingValue = "true",
    matchIfMissing = true
)
class IpCommand : IBotCommand {

    private val log = LogManager.getLogger(IpCommand::class.java)

    override fun name(): String = "ip"

    override fun description(): String = "查询 IP 信息"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("查询 IP 地址信息", UsageNode.arg("ip_address"))
            .param("ip_address", "IP 地址（IPv4 / IPv6）")
            .example(
                "ip 1.1.1.1",
                "ip 2606:4700:4700::1111"
            )
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    argument("ip", StringArgumentType.greedyString())
                        .executes { ctx ->
                            try {
                                val ip = StringArgumentType.getString(ctx, "ip")
                                ctx.source.reply(getIpInfo(ip).toString())
                                1
                            } catch (e: IOException) {
                                log.warn("Failed to fetch ip information.", e)
                                ctx.source.reply(e.message ?: "Failed to fetch ip information.")
                                0
                            }
                        }
                )
        )
    }

    companion object {
        fun getIpInfo(ip: String?): IpInfo {
            val result: JsonObject = HttpJson.getObject("https://api.ip.sb/geoip/$ip")

            return IpInfo(
                ip = JsonUtils.getString(result, "ip", null),
                country = JsonUtils.getString(result, "country", null),
                countryCode = JsonUtils.getString(result, "country_code", null),
                region = JsonUtils.getString(result, "region", null),
                regionCode = JsonUtils.getString(result, "region_code", null),
                city = JsonUtils.getString(result, "city", null),
                postalCode = JsonUtils.getString(result, "postal_code", null),
                latitude = JsonUtils.getString(result, "latitude", null),
                longitude = JsonUtils.getString(result, "longitude", null),
                org = JsonUtils.getString(result, "organization", null),
                timezone = JsonUtils.getString(result, "timezone", null),
                asn = JsonUtils.getString(result, "asn", null)
            )
        }

        data class IpInfo(
            val ip: String?,
            val country: String?,
            val countryCode: String?,
            val region: String?,
            val regionCode: String?,
            val city: String?,
            val postalCode: String?,
            val latitude: String?,
            val longitude: String?,
            val org: String?,
            val timezone: String?,
            val asn: String?
        ) {
            override fun toString(): String = buildString {
                append("IP 地址 - ").append(ip ?: "未知").append('\n')

                appendLineIfPresent(
                    label = "国家/地区：",
                    main = country,
                    extra = countryCode?.let { " ($it)" }
                )

                appendLineIfPresent(
                    label = "区域：",
                    main = region,
                    extra = regionCode?.let { " ($it)" }
                )

                appendLineIfPresent(
                    label = "城市：",
                    main = city,
                    extra = postalCode?.let { " ($it)" }
                )

                if (!latitude.isNullOrBlank() && !longitude.isNullOrBlank()) {
                    append("位置：").append(latitude).append(", ").append(longitude).append('\n')
                }

                appendLineIfPresent("时区：", timezone)
                appendLineIfPresent("组织：", org)
                appendLineIfPresent("ASN：", asn)
            }

            private fun StringBuilder.appendLineIfPresent(
                label: String,
                main: String?,
                extra: String? = null
            ) {
                if (main.isNullOrBlank()) return
                append(label).append(main)
                if (!extra.isNullOrBlank()) append(extra)
                append('\n')
            }
        }
    }
}
