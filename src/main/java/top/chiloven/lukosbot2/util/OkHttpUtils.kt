package top.chiloven.lukosbot2.util

import okhttp3.OkHttpClient
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.util.concurrent.TimeUnit

/**
 * Shared helpers for creating proxy-aware OkHttp clients.
 *
 * Several utilities in the project keep their own cached [OkHttpClient] because they need slightly
 * different timeout policies, but they all derive that client from the same runtime proxy bean.
 * This file centralizes the proxy lookup and cache-key logic, and provides a small reusable client
 * cache implementation for the common pattern.
 */
object OkHttpUtils {

    const val NO_PROXY_CACHE_KEY: String = "NO_PROXY"

    /**
     * Create a new [OkHttpClient.Builder] with shared timeout, redirect and proxy configuration.
     *
     * This is the common entry point for places that need a builder instead of a fully built client,
     * such as third-party SDK initializers.
     */
    @JvmStatic
    @JvmOverloads
    fun newBuilder(
        proxy: ProxyConfigProp? = proxyOrNull(),
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
        callTimeoutMs: Long? = null,
        followRedirects: Boolean = true,
        followSslRedirects: Boolean = true,
    ): OkHttpClient.Builder {
        return OkHttpClient.Builder().apply {
            if (connectTimeoutMs != null) {
                connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            }
            if (readTimeoutMs != null) {
                readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            }
            if (callTimeoutMs != null) {
                callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            }
            followRedirects(followRedirects)
            followSslRedirects(followSslRedirects)
            if (proxy?.isEnabled == true) {
                proxy.applyTo(this)
            }
        }
    }

    /**
     * Try to obtain [ProxyConfigProp] from the Spring container.
     *
     * Returning `null` is considered a normal outcome when the application is running in a context
     * where the bean is not available.
     */
    @JvmStatic
    fun proxyOrNull(): ProxyConfigProp? = try {
        SpringBeans.getBean(ProxyConfigProp::class.java)
    } catch (_: Throwable) {
        null
    }

    /**
     * Build a stable cache key for the current proxy configuration.
     */
    @JvmStatic
    fun proxyKey(proxy: ProxyConfigProp?): String {
        if (proxy == null || !proxy.isEnabled) return NO_PROXY_CACHE_KEY
        return listOf(
            proxy.isEnabled,
            proxy.type,
            proxy.host,
            proxy.port,
            proxy.username,
            proxy.password,
            proxy.nonProxyHostsList?.joinToString("|")
        ).joinToString("#")
    }

    /**
     * Lightweight cache for a single proxy-aware [OkHttpClient] configuration.
     *
     * Each owning utility can keep one instance of this class and request [client] whenever it needs a
     * base client. The cache is invalidated automatically when the effective proxy configuration
     * changes.
     */
    class ProxyAwareOkHttpClientCache(
        private val connectTimeoutMs: Long,
        private val readTimeoutMs: Long? = null,
        private val callTimeoutMs: Long? = null,
        private val followRedirects: Boolean = true,
        private val followSslRedirects: Boolean = true,
        private val proxyProvider: () -> ProxyConfigProp? = { proxyOrNull() },
        private val configureBuilder: OkHttpClient.Builder.() -> Unit = {}
    ) {

        @Volatile
        private var cachedClient: OkHttpClient? = null

        @Volatile
        private var cachedKey: String? = null

        val client: OkHttpClient
            get() {
                val proxy = proxyProvider()
                val key = proxyKey(proxy)

                cachedClient?.let { existing ->
                    if (cachedKey == key) return existing
                }

                synchronized(this) {
                    cachedClient?.let { existing ->
                        if (cachedKey == key) return existing
                    }

                    val builder = newBuilder(
                        proxy = proxy,
                        connectTimeoutMs = connectTimeoutMs,
                        readTimeoutMs = readTimeoutMs,
                        callTimeoutMs = callTimeoutMs,
                        followRedirects = followRedirects,
                        followSslRedirects = followSslRedirects,
                    ).apply(configureBuilder)

                    return builder.build().also {
                        cachedClient = it
                        cachedKey = key
                    }
                }
            }

    }

}
