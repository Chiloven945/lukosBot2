package top.chiloven.lukosbot2.commands.impl.ip

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.impl.ip.IpQueryResult.IpProviderFailure
import top.chiloven.lukosbot2.commands.impl.ip.IpQueryResult.IpQueryException
import top.chiloven.lukosbot2.commands.impl.ip.provider.IIpProvider

@Service
class IpQueryService(
    providers: List<IIpProvider>
) {

    private val log = LogManager.getLogger(IpQueryService::class.java)

    private val orderedProviders: List<IIpProvider> = providers.sortedByDescending { it.priority() }

    private val providerIndex: Map<String, IIpProvider> = orderedProviders
        .flatMap { provider ->
            (setOf(provider.id()) + provider.aliases())
                .map { it.lowercase() to provider }
        }
        .toMap()

    fun availableProvidersText(): String {
        if (orderedProviders.isEmpty()) return "暂无可用数据源"

        return orderedProviders.joinToString("、") {
            "${it.id()}(优先级 ${it.priority()})"
        }
    }

    fun query(
        ip: String,
        requestedProviders: List<String> = emptyList()
    ): IpQueryResult {
        val normalizedIp = ip.trim()
        require(normalizedIp.isNotBlank()) {
            "请输入要查询的 IP 地址"
        }

        val targets = selectProviders(requestedProviders)
        if (targets.isEmpty()) {
            throw IllegalStateException("没有可用的 IP 数据源")
        }

        val failures = mutableListOf<IpProviderFailure>()

        for (provider in targets) {
            try {
                val data = provider.query(normalizedIp)
                return IpQueryResult(
                    providerId = provider.id(),
                    data = data,
                    failures = failures.toList()
                )
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                failures += IpProviderFailure(provider.id(), reason)
                log.warn(
                    "IP provider query failed. provider={}, ip={}",
                    provider.id(),
                    normalizedIp,
                    e
                )
            }
        }

        throw IpQueryException(
            ip = normalizedIp,
            providerIds = targets.map { it.id() },
            failures = failures
        )
    }

    private fun selectProviders(requestedProviders: List<String>): List<IIpProvider> {
        if (requestedProviders.isEmpty()) return orderedProviders

        val result = mutableListOf<IIpProvider>()
        val unknown = mutableListOf<String>()

        for (raw in requestedProviders) {
            val key = raw.trim().lowercase()
            if (key.isBlank()) continue

            val provider = providerIndex[key]
            if (provider == null) {
                unknown += raw
            } else if (result.none { it.id() == provider.id() }) {
                result += provider
            }
        }

        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException(
                "未知 IP 数据源：${unknown.joinToString("、")}。可用数据源：${availableProvidersText()}"
            )
        }

        return result
    }

}
