package top.chiloven.lukosbot2.commands.impl.ip.provider.impl

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.commands.impl.ip.IpData
import top.chiloven.lukosbot2.commands.impl.ip.provider.IIpProvider
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.elm
import top.chiloven.lukosbot2.util.JsonUtils.str

@Component
class IpSbProvider : IIpProvider {

    private companion object {

        private const val BASE = "https://api.ip.sb"

    }

    override fun id(): String = "ipsb"

    override fun aliases(): Set<String> = setOf("ip.sb", "ip-sb")

    override fun priority(): Int = 80

    override fun query(ip: String): IpData {
        val obj = HttpJson.getObject("$BASE/geoip/$ip")

        return IpData(
            ip = obj.str("ip") ?: ip,
            country = obj.str("country"),
            countryCode = obj.str("country_code"),
            region = obj.str("region"),
            regionCode = obj.str("region_code"),
            city = obj.str("city"),
            postalCode = obj.str("postal_code"),
            latitude = obj.elm("latitude")?.asDouble(),
            longitude = obj.elm("longitude")?.asDouble(),
            timezone = obj.str("timezone"),
            asn = obj.str("asn"),
            org = obj.str("organization")
        )
    }

}
