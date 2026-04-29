package top.chiloven.lukosbot2.commands.impl.ip

data class IpQueryResult(
    val providerId: String,
    val data: IpData,
    val failures: List<IpProviderFailure> = emptyList()
) {

    fun toDisplayText(): String = buildString {
        append(data.toDisplayText(providerId))

        if (failures.isNotEmpty()) {
            append("\n\n")
            append("提示：部分数据源暂时不可用，已自动切换到 ")
            append(providerId)
            append("。")
            append("不可用数据源：")
            append(failures.joinToString("、") { it.providerId })
        }
    }

    data class IpProviderFailure(
        val providerId: String,
        val reason: String
    )

    class IpQueryException(
        val ip: String,
        val providerIds: List<String>,
        val failures: List<IpProviderFailure>
    ) : RuntimeException(
        buildString {
            append("IP 查询失败，所有数据源均不可用")
            if (providerIds.isNotEmpty()) {
                append("。已尝试：")
                append(providerIds.joinToString("、"))
            }
            if (failures.isNotEmpty()) {
                append("。失败原因：")
                append(failures.joinToString("；") { "${it.providerId}: ${it.reason}" })
            }
        }
    )

}
