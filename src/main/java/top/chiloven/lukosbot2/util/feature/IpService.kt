package top.chiloven.lukosbot2.util.feature

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.getString
import java.io.IOException

class IpService {

    @Throws(IOException::class)
    fun getIpInfo(ip: String?): IpInfo {
        val result: JsonObject = hj.getObject("https://api.ip.sb/geoip/$ip")

        return IpInfo(
            ip = getString(result, "ip", null),
            country = getString(result, "country", null),
            countryCode = getString(result, "country_code", null),
            region = getString(result, "region", null),
            regionCode = getString(result, "region_code", null),
            city = getString(result, "city", null),
            postalCode = getString(result, "postal_code", null),
            latitude = getString(result, "latitude", null),
            longitude = getString(result, "longitude", null),
            org = getString(result, "organization", null),
            timezone = getString(result, "timezone", null),
            asn = getString(result, "asn", null)
        )
    }

    @JvmRecord
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

    companion object {
        val hj: HttpJson = HttpJson.getHttpJson()
    }
}
