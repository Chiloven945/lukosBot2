package top.chiloven.lukosbot2.commands.impl.motd

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.OkHttpUtils
import java.io.IOException
import java.util.*

@Service
class MotdQueryService(
    private val proxyConfigProp: ProxyConfigProp,
) {

    companion object {

        private const val DEFAULT_PORT = 25565
        private const val API_BASE_URL = "https://api.mcsrvstat.us"
        private const val API_CONNECT_TIMEOUT_SECONDS = 8L
        private const val API_READ_TIMEOUT_SECONDS = 12L
        private const val API_CALL_TIMEOUT_SECONDS = 12L
        private const val FALLBACK_TIMEOUT_MS = 60_500
        private const val DEFAULT_DESCRIPTION = "A Minecraft Server"
        private const val FAVICON_PREFIX = "data:image/png;base64,"

    }

    private val log = LogManager.getLogger(MotdQueryService::class.java)

    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = API_CONNECT_TIMEOUT_SECONDS * 1000,
        readTimeoutMs = API_READ_TIMEOUT_SECONDS * 1000,
        callTimeoutMs = API_CALL_TIMEOUT_SECONDS * 1000,
        proxyProvider = { proxyConfigProp },
    )

    private val okHttp
        get() = clientCache.client

    fun query(rawAddress: String, mode: QueryMode = QueryMode.AUTO): MotdQueryResult {
        val address = MinecraftServerAddress.parse(rawAddress)

        return when (mode) {
            QueryMode.AUTO -> queryAuto(address)
            QueryMode.API -> queryByApi(address)
            QueryMode.DIRECT -> queryByDirectPing(address)
        }
    }

    private fun queryAuto(address: MinecraftServerAddress): MotdQueryResult {
        return try {
            queryByApi(address)
        } catch (apiError: Exception) {
            log.warn("MOTD API query failed for {}: {}", address.normalized(), apiError.message)
            queryByDirectPing(address, listOf("mcsrvstat.us：${apiError.message ?: "查询失败"}"))
        }
    }

    private fun queryByApi(address: MinecraftServerAddress): MotdQueryResult {
        val url = API_BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("3")
            .addPathSegment(address.apiAddress())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "${Constants.UA} motd")
            .header("Accept", "application/json")
            .build()

        okHttp.newCall(request).execute().use { response ->
            val body = response.body.string().trim()
            if (!response.isSuccessful) {
                val detail = body.ifBlank { response.message.ifBlank { "HTTP ${response.code}" } }
                throw IOException("mcsrvstat.us 查询失败（HTTP ${response.code}）：$detail")
            }
            if (body.isBlank()) {
                throw IOException("mcsrvstat.us 没有返回可用内容")
            }

            val root = JsonUtils.MAPPER.readTree(body).asObjectOpt().orElseThrow {
                IOException("mcsrvstat.us 返回的数据格式无法识别")
            }
            return McSrvStatusResponse.fromJsonObject(root).toQueryResult(address)
        }
    }

    private fun queryByDirectPing(
        address: MinecraftServerAddress,
        initialFailures: List<String> = emptyList(),
    ): MotdQueryResult {
        val candidates = MinecraftJavaAddressResolver.resolveCandidates(address)
        val failures = initialFailures.toMutableList()

        for (candidate in candidates) {
            runCatching {
                val status = MinecraftJavaStatusPinger.ping(
                    endpoint = candidate,
                    proxyConfigProp = proxyConfigProp,
                    timeoutMs = FALLBACK_TIMEOUT_MS,
                )
                return MotdQueryResult(
                    requestedAddress = address.normalized(),
                    resolvedAddress = candidate.displayAddress(),
                    ip = status.remoteIp,
                    online = true,
                    versionName = status.version,
                    protocolVersion = status.protocol.takeIf { it >= 0 },
                    onlinePlayers = status.onlinePlayers,
                    maxPlayers = status.maxPlayers,
                    descriptionLines = status.description.lines().filter { it.isNotBlank() }
                        .ifEmpty { listOf(DEFAULT_DESCRIPTION) },
                    favicon = status.favicon,
                    software = null,
                    srvResolved = candidate.viaSrv,
                    eulaBlocked = null,
                    source = DataSource.DIRECT,
                )
            }.onFailure { pingError ->
                failures += "${candidate.displayAddress()}: ${pingError.message ?: pingError::class.java.simpleName}"
            }
        }

        throw IOException("无法获取服务器状态：${failures.joinToString("；")}")
    }

    private fun McSrvStatusResponse.toQueryResult(address: MinecraftServerAddress): MotdQueryResult {
        val portText = port ?: address.port ?: DEFAULT_PORT
        val resolvedHost = hostname?.takeIf { it.isNotBlank() } ?: address.hostForDisplay()

        return MotdQueryResult(
            requestedAddress = address.normalized(),
            resolvedAddress = MinecraftServerAddress.format(resolvedHost, portText),
            ip = ip?.takeIf { it.isNotBlank() },
            online = online,
            versionName = protocol?.name?.takeIf { it.isNotBlank() }
                ?: version?.takeIf { it.isNotBlank() }
                ?: if (online) "未知" else null,
            protocolVersion = protocol?.version,
            onlinePlayers = players?.online ?: 0,
            maxPlayers = players?.max ?: 0,
            descriptionLines = descriptionLines(),
            favicon = icon?.takeIf { it.isNotBlank() },
            software = software?.takeIf { it.isNotBlank() },
            srvResolved = debug?.srv == true,
            eulaBlocked = eulaBlocked,
            source = DataSource.MCSRVSTAT,
        )
    }

    private fun McSrvStatusResponse.descriptionLines(): List<String> {
        val clean = motd?.clean.orEmpty().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (clean.isNotEmpty()) return clean

        val raw = motd?.raw.orEmpty()
            .map { it.replace(Regex("§."), "").trimEnd() }
            .filter { it.isNotBlank() }
        if (raw.isNotEmpty()) return raw

        return listOf(DEFAULT_DESCRIPTION)
    }

    enum class QueryMode(
        val token: String,
        val aliases: Set<String>,
        val displayName: String,
    ) {

        AUTO(
            token = "auto",
            aliases = setOf("auto", "默认", "自动"),
            displayName = "自动"
        ),
        API(
            token = "api",
            aliases = setOf("api"),
            displayName = "API"
        ),
        DIRECT(
            token = "direct",
            aliases = setOf("direct", "self", "direct-ping", "直连", "自解析", "自己解析"),
            displayName = "直连"
        );

        companion object {

            fun parse(raw: String): QueryMode? {
                val normalized = raw.trim().lowercase(Locale.ROOT)
                return entries.firstOrNull {
                    normalized in it.aliases.map { alias -> alias.lowercase(Locale.ROOT) }.toSet()
                }
            }

        }
    }

    enum class DataSource {
        MCSRVSTAT,
        DIRECT,
    }

    data class MotdQueryResult(
        val requestedAddress: String,
        val resolvedAddress: String,
        val ip: String?,
        val online: Boolean,
        val versionName: String?,
        val protocolVersion: Int?,
        val onlinePlayers: Int,
        val maxPlayers: Int,
        val descriptionLines: List<String>,
        val favicon: String?,
        val software: String?,
        val srvResolved: Boolean,
        val eulaBlocked: Boolean?,
        val source: DataSource,
    ) {

        fun formatted(): String {
            val lines = mutableListOf<String>()
            if (requestedAddress != resolvedAddress) {
                lines += "查询：$requestedAddress"
            }
            lines += "地址：$resolvedAddress"
            ip?.takeIf { it.isNotBlank() }?.let { lines += "IP：$it" }
            lines += "状态：${if (online) "在线" else "离线"}"
            lines += "描述："
            lines += descriptionLines.ifEmpty { listOf(DEFAULT_DESCRIPTION) }

            if (online) {
                versionName?.let { version ->
                    val protocolSuffix = protocolVersion?.let { "（$it）" }.orEmpty()
                    lines += "版本：$version$protocolSuffix"
                }
                lines += "玩家：$onlinePlayers/$maxPlayers"
                software?.let { lines += "软件：$it" }
            }

            if (srvResolved) {
                lines += "SRV：已解析"
            }
            if (eulaBlocked == true) {
                lines += "EULA 限制：是"
            }
            if (source == DataSource.MCSRVSTAT) {
                lines += "数据来源：mcsrvstat.us"
            }
            if (source == DataSource.DIRECT) {
                lines += "数据来源：直连协议"
            }

            return lines.joinToString("\n").trim()
        }

        fun faviconBytes(): ByteArray? {
            val raw = favicon?.takeIf { it.startsWith(FAVICON_PREFIX) } ?: return null
            val payload = raw.removePrefix(FAVICON_PREFIX)
            return runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
        }

    }

}
