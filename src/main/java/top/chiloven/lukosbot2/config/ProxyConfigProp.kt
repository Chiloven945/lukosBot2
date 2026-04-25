package top.chiloven.lukosbot2.config

import jakarta.annotation.PostConstruct
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.apache.logging.log4j.LogManager
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.*
import java.net.http.HttpClient
import java.util.*

/**
 * Proxy configuration for the application.
 *
 * Properties are read from `lukos.proxy.*` (application.yml / application.properties).
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "lukos.proxy")
data class ProxyConfigProp(
    var enabled: Boolean = false,
    var type: String = "NONE",
    var host: String? = null,
    var port: Int = 0,
    var username: String? = null,
    var password: String? = null,
    var nonProxyHostsList: List<String>? = null,
) {

    private val log = LogManager.getLogger(ProxyConfigProp::class.java)

    /**
     * Apply proxy configuration JVM-wide ASAP.
     *
     * This sets standard system properties for HTTP/HTTPS or SOCKS proxies.
     * It may affect libraries that read JVM proxy properties implicitly.
     */
    @PostConstruct
    fun applyJvmWide() {
        if (!enabled) return

        val bypass = nonProxyHostsList
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("|")
            .orEmpty()

        if (bypass.isNotBlank()) {
            System.setProperty("http.nonProxyHosts", bypass)
            System.setProperty("socksNonProxyHosts", bypass)
        }

        if (!hasValidEndpoint()) {
            if (normalizedType() != NormalizedType.NONE) {
                log.error(
                    "[ProxyConfig] proxy enabled but host/port is invalid, skip applying JVM proxy: host={}, port={}",
                    host,
                    port
                )
            }
            return
        }

        val addr = toInetSocketAddress()
        when (normalizedType()) {
            NormalizedType.HTTP -> {
                System.setProperty("http.proxyHost", host.orEmpty())
                System.setProperty("http.proxyPort", port.toString())
                System.setProperty("https.proxyHost", host.orEmpty())
                System.setProperty("https.proxyPort", port.toString())
            }

            NormalizedType.SOCKS5 -> {
                System.setProperty("socksProxyHost", host.orEmpty())
                System.setProperty("socksProxyPort", port.toString())
                System.setProperty("socksProxyVersion", "5")

                // Best-effort: install JVM Authenticator for SOCKS auth (global).
                if (notBlank(username)) {
                    val u = username.orEmpty()
                    val p = password.orEmpty()
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(u, p.toCharArray())
                        }
                    })
                }
            }

            NormalizedType.NONE -> Unit
        }

        log.info(
            "[ProxyConfig] Applied JVM proxy: type={}, host={}, port={}",
            normalizedType(),
            addr.hostString,
            addr.port
        )
    }

    private fun hasValidEndpoint(): Boolean {
        return enabled && notBlank(host) && port > 0 && normalizedType() != NormalizedType.NONE
    }

    private fun normalizedType(): NormalizedType {
        return when (type.trim().uppercase(Locale.ROOT)) {
            "HTTP", "HTTPS" -> NormalizedType.HTTP
            "SOCKS", "SOCKS5" -> NormalizedType.SOCKS5
            "NONE" -> NormalizedType.NONE
            else -> {
                log.warn(
                    "[ProxyConfig] Unknown proxy type: {} (expected NONE/HTTP/HTTPS/SOCKS/SOCKS5). Using NONE.",
                    type
                )
                NormalizedType.NONE
            }
        }
    }

    private fun toInetSocketAddress(): InetSocketAddress = InetSocketAddress(host.orEmpty(), port)

    /**
     * Apply proxy configuration to OkHttp.
     *
     * HTTP proxy authentication will be set via `Proxy-Authorization` header.
     * For SOCKS proxy, OkHttp does not support username/password directly; see class note.
     */
    fun applyTo(builder: OkHttpClient.Builder?) {
        if (builder == null) return
        if (!hasValidEndpoint()) return

        val proxy = toJavaProxy()
        if (proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy)
        }

        if (normalizedType() == NormalizedType.HTTP && notBlank(username)) {
            val cred = Credentials.basic(username.orEmpty(), password.orEmpty())
            builder.proxyAuthenticator { _, response ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", cred)
                    .build()
            }
        }

        // SOCKS auth: best-effort only; recommend JVM-wide authenticator.
        if (normalizedType() == NormalizedType.SOCKS5 && notBlank(username)) {
            log.warn("[ProxyConfig] SOCKS username/password is best-effort only for OkHttp. Consider relying on JVM Authenticator (applyJvmWide).")
        }
    }

    /**
     * Convert configuration to [Proxy].
     *
     * @return [Proxy.NO_PROXY] if disabled/invalid/none.
     */
    fun toJavaProxy(): Proxy {
        if (!hasValidEndpoint()) return Proxy.NO_PROXY

        val address = toInetSocketAddress()
        return when (normalizedType()) {
            NormalizedType.SOCKS5 -> Proxy(Proxy.Type.SOCKS, address)
            NormalizedType.HTTP -> Proxy(Proxy.Type.HTTP, address)
            NormalizedType.NONE -> Proxy.NO_PROXY
        }
    }

    /**
     * Apply proxy configuration to JDK [HttpClient].
     */
    fun applyTo(b: HttpClient.Builder?): HttpClient.Builder {
        val builder = b ?: HttpClient.newBuilder()
        if (!hasValidEndpoint()) return builder

        val proxy = toJavaProxy()
        val address = proxy.address()
        if (proxy != Proxy.NO_PROXY && address is InetSocketAddress) {
            builder.proxy(ProxySelector.of(address))
        }

        if (notBlank(username)) {
            val u = username.orEmpty()
            val p = password.orEmpty()
            builder.authenticator(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(u, p.toCharArray())
                }
            })
        }

        return builder
    }

    /**
     * Chromium proxy argument.
     *
     * @return argument string like `--proxy-server=socks5://host:port` or
     * `--proxy-server=http://host:port`, or `null` if proxy is disabled / invalid.
     */
    fun chromiumProxyArg(): String? {
        if (!hasValidEndpoint()) return null

        val proxy = toJavaProxy()
        if (proxy == Proxy.NO_PROXY) return null

        val addr = proxy.address() as? InetSocketAddress ?: return null
        val scheme = if (proxy.type() == Proxy.Type.SOCKS) "socks5" else "http"
        return "--proxy-server=$scheme://${printableHost(addr)}:${addr.port}"
    }

    /**
     * Selenium proxy object for Edge/Chrome.
     *
     * @return Selenium proxy or `null` if proxy disabled/invalid.
     */
    fun toSeleniumProxy(): org.openqa.selenium.Proxy? {
        if (!hasValidEndpoint()) return null

        val proxy = toJavaProxy()
        if (proxy == Proxy.NO_PROXY) return null

        val addr = proxy.address() as? InetSocketAddress ?: return null
        val hostPort = "${printableHost(addr)}:${addr.port}"
        val sp = org.openqa.selenium.Proxy()

        if (proxy.type() == Proxy.Type.SOCKS) {
            sp.setSocksProxy(hostPort)
            sp.setSocksVersion(5)
        } else {
            sp.setHttpProxy(hostPort)
            sp.setSslProxy(hostPort)
        }

        return sp
    }

    private enum class NormalizedType {
        NONE,
        HTTP,
        SOCKS5
    }

    companion object {

        private fun notBlank(s: String?): Boolean = !s.isNullOrBlank()

        private fun printableHost(addr: InetSocketAddress): String {
            val h = addr.hostString
            return if (h.contains(":") && !h.startsWith("[")) "[$h]" else h
        }

    }

}
