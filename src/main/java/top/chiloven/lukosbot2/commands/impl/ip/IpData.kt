package top.chiloven.lukosbot2.commands.impl.ip

import java.util.*

data class IpData(
    val ip: String,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val regionCode: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val asn: String? = null,
    val org: String? = null,
    val isp: String? = null,
    val risk: IpRisk? = null
) {

    fun toDisplayText(providerId: String? = null): String = buildString {
        append("IP 地址：").append(ip.ifBlank { "未知" }).append('\n')

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

        if (latitude != null && longitude != null) {
            append("位置：")
                .append(String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude))
                .append('\n')
        }

        appendLineIfPresent("时区：", timezone)
        appendLineIfPresent("组织：", org)
        appendLineIfPresent("ISP：", isp)
        appendLineIfPresent("ASN：", asn)

        risk?.let { r ->
            val flags = buildList {
                if (r.isMobile == true) add("移动网络")
                if (r.isVpn == true) add("VPN")
                if (r.isTor == true) add("Tor")
                if (r.isProxy == true) add("Proxy")
                if (r.isDatacenter == true) add("机房")
            }

            if (flags.isNotEmpty()) {
                append("风险标记：").append(flags.joinToString(" / ")).append('\n')
            }

            r.riskScore?.let {
                append("风险分：").append(it).append("/100").append('\n')
            }
        }

        if (!providerId.isNullOrBlank()) {
            append("数据源：").append(providerId).append('\n')
        }
    }.trimEnd()

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

    data class IpRisk(
        val isMobile: Boolean? = null,
        val isVpn: Boolean? = null,
        val isTor: Boolean? = null,
        val isProxy: Boolean? = null,
        val isDatacenter: Boolean? = null,
        val riskScore: Int? = null
    )

}
