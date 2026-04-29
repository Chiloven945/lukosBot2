package top.chiloven.lukosbot2.commands.impl.ip.provider.impl

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.commands.impl.ip.IpData
import top.chiloven.lukosbot2.commands.impl.ip.provider.IIpProvider
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.bool
import top.chiloven.lukosbot2.util.JsonUtils.elm
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str

@Component
class IpQueryIoProvider : IIpProvider {

    private companion object {

        private const val BASE = "https://api.ipquery.io"

    }

    override fun id(): String = "ipquery"

    override fun aliases(): Set<String> = setOf("ipquery.io", "ip-query")

    override fun priority(): Int = 100

    override fun query(ip: String): IpData {
        val obj = HttpJson.getObject("$BASE/$ip")

        val isp = obj.obj("isp")
        val location = obj.obj("location")
        val risk = obj.obj("risk")

        return IpData(
            ip = obj.str("ip") ?: ip,
            country = location?.str("country"),
            countryCode = location?.str("country_code"),
            region = location?.str("state"),
            city = location?.str("city"),
            postalCode = location?.str("zipcode"),
            latitude = location?.elm("latitude")?.asDouble(),
            longitude = location?.elm("longitude")?.asDouble(),
            timezone = location?.str("timezone"),
            asn = isp?.str("asn"),
            org = isp?.str("org"),
            isp = isp?.str("isp"),
            risk = risk?.let {
                IpData.IpRisk(
                    isMobile = it.bool("is_mobile"),
                    isVpn = it.bool("is_vpn"),
                    isTor = it.bool("is_tor"),
                    isProxy = it.bool("is_proxy"),
                    isDatacenter = it.bool("is_datacenter"),
                    riskScore = it.int("risk_score")
                )
            }
        )
    }

}
