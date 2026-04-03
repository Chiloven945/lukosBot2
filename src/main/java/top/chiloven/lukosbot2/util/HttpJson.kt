package top.chiloven.lukosbot2.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.logging.log4j.LogManager
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.HttpJson.DEFAULT_HEADERS
import top.chiloven.lukosbot2.util.HttpJson.decodeByContentEncoding
import top.chiloven.lukosbot2.util.HttpJson.getAny
import top.chiloven.lukosbot2.util.HttpJson.getArray
import top.chiloven.lukosbot2.util.HttpJson.getObject
import top.chiloven.lukosbot2.util.JsonUtils.MAPPER
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Lightweight HTTP GET + JSON helper built on top of OkHttp and Jackson.
 *
 * This utility exists for the common pattern used by many commands and features in the project:
 *
 * 1. Build a URL, optionally with query parameters.
 * 2. Perform an HTTP GET request.
 * 3. Decode the body according to `Content-Encoding` and `Content-Type`.
 * 4. Parse the body as JSON.
 * 5. Optionally assert that the JSON root is an object or array.
 *
 * ## Scope and design goals
 *
 * `HttpJson` intentionally stays small and opinionated:
 *
 * - Only **GET** requests are supported.
 * - The return type is always Jackson tree model ([JsonNode], [ObjectNode], or [ArrayNode]).
 * - Proxy configuration is auto-discovered from Spring when available.
 * - Default headers are tuned for JSON APIs.
 * - Error reporting tries to surface human-readable API error messages when possible.
 *
 * It is meant for straightforward integrations and command-side HTTP calls, not as a general HTTP
 * abstraction layer.
 *
 * ## Connection reuse
 *
 * The object caches a single [OkHttpClient] instance and recreates it only when the effective proxy
 * configuration changes. This keeps connection pooling and TLS/session reuse efficient while still
 * respecting runtime configuration.
 *
 * ## Response decoding
 *
 * The implementation supports the following content encodings:
 * - `identity`
 * - `gzip`
 * - `deflate`
 *
 * Charset is inferred from the `Content-Type` header when possible; otherwise UTF-8 is used.
 *
 * ## Failure behavior
 *
 * - Transport problems raise [IOException].
 * - HTTP status codes `>= 400` raise [IOException].
 * - If the error body is JSON and contains fields such as `message`, `error`, or `detail`, the
 *   extracted text is used as the exception message.
 * - If a method expects an object/array root but the parsed JSON root has another type,
 *   [IllegalArgumentException] is raised.
 *
 * @author Chiloven945
 */
object HttpJson {

    private val log = LogManager.getLogger(HttpJson::class.java)

    /**
     * Default per-call timeout in milliseconds used by public request helpers unless the caller
     * explicitly overrides it.
     */
    private const val DEFAULT_READ_TIMEOUT: Int = 10_000

    /**
     * Default request headers used by the helper methods.
     *
     * The utility explicitly requests JSON responses and asks servers to avoid compression unless
     * the caller overrides the header set.
     */
    private val DEFAULT_HEADERS: Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Encoding" to "identity"
    )

    /**
     * Shared user-agent sent with every request.
     */
    private const val UA: String = "Mozilla/5.0 (compatible; ${Constants.UA})"

    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedKey: String? = null

    /**
     * Build a stable cache key for the current proxy configuration.
     *
     * The key is used to decide whether the shared [OkHttpClient] can be reused or must be rebuilt.
     */
    private fun proxyKey(p: ProxyConfigProp): String =
        listOf(
            p.isEnabled,
            p.type,
            p.host,
            p.port,
            p.username,
            p.password,
            p.nonProxyHostsList?.joinToString("|")
        ).joinToString("#")

    /**
     * Try to obtain [ProxyConfigProp] from the Spring container.
     *
     * Returning `null` is considered a normal outcome when the application is running in a context
     * where the bean is not available.
     */
    private fun proxyOrNull(): ProxyConfigProp? = try {
        SpringBeans.getBean(ProxyConfigProp::class.java)
    } catch (_: Throwable) {
        null
    }

    /**
     * Lazily initialized and proxy-aware [OkHttpClient].
     *
     * The client is cached and reused as long as the derived proxy cache key remains unchanged.
     * This allows the utility to benefit from OkHttp connection pooling without permanently locking
     * itself to an outdated proxy configuration.
     */
    private val client: OkHttpClient
        get() {
            val proxy = proxyOrNull()
            val key = if (proxy != null && proxy.isEnabled) proxyKey(proxy) else "NO_PROXY"

            val c = cachedClient
            if (c != null && cachedKey == key) return c

            synchronized(this) {
                val c2 = cachedClient
                if (c2 != null && cachedKey == key) return c2

                val b = OkHttpClient.Builder()
                    .connectTimeout(10L, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)

                if (proxy != null && proxy.isEnabled) {
                    proxy.applyTo(b)
                }

                val built = b.build()
                cachedClient = built
                cachedKey = key
                return built
            }
        }

    /**
     * Decode a raw response body according to the declared `Content-Encoding` header.
     *
     * The method supports chained encodings separated by commas and applies them in order.
     * Unsupported encodings cause an [IOException].
     *
     * @param raw raw response bytes as received from the server
     * @param encoding raw `Content-Encoding` header value
     * @return decoded payload bytes
     * @throws IOException if an unsupported encoding is encountered or decoding fails
     */
    @Throws(IOException::class)
    private fun decodeByContentEncoding(raw: ByteArray, encoding: String): ByteArray {
        if (encoding.isEmpty() || encoding == "identity") return raw

        val encs = encoding.split(',')
        var data = raw

        for (enc in encs) {
            val e = enc.trim().lowercase()
            when (e) {
                "", "identity" -> Unit
                "gzip" -> data = gunzip(data)
                "deflate" -> data = inflate(data)
                else -> throw IOException("Unsupported Content-Encoding: $encoding")
            }
        }
        return data
    }

    /**
     * Expand a GZIP-compressed payload.
     *
     * @param gz compressed bytes
     * @return decompressed bytes
     * @throws IOException if decompression fails
     */
    @Throws(IOException::class)
    private fun gunzip(gz: ByteArray): ByteArray {
        ByteArrayInputStream(gz).use { bin ->
            GZIPInputStream(bin).use { gin ->
                ByteArrayOutputStream().use { out ->
                    gin.transferTo(out)
                    return out.toByteArray()
                }
            }
        }
    }

    /**
     * Expand a DEFLATE-compressed payload.
     *
     * @param def compressed bytes
     * @return decompressed bytes
     * @throws IOException if decompression fails
     */
    @Throws(IOException::class)
    private fun inflate(def: ByteArray): ByteArray {
        ByteArrayInputStream(def).use { bin ->
            InflaterInputStream(bin).use { input ->
                ByteArrayOutputStream().use { out ->
                    input.transferTo(out)
                    return out.toByteArray()
                }
            }
        }
    }

    /**
     * Extract the charset name from a `Content-Type` header.
     *
     * If no charset is declared, or if the declaration is blank/malformed for the simple cases this
     * helper supports, UTF-8 is used.
     *
     * @param contentType raw `Content-Type` header value
     * @return resolved charset name, defaulting to `"UTF-8"`
     */
    private fun extractCharset(contentType: String?): String {
        if (contentType.isNullOrBlank()) return "UTF-8"
        val ct = contentType.lowercase()
        val i = ct.indexOf("charset=")
        if (i < 0) return "UTF-8"

        var cs = ct.substring(i + "charset=".length).trim()
        val semi = cs.indexOf(';')
        if (semi >= 0) cs = cs.substring(0, semi).trim()
        if (cs.isEmpty()) return "UTF-8"
        if (cs.startsWith("\"") && cs.endsWith("\"") && cs.length >= 2) {
            cs = cs.substring(1, cs.length - 1)
        }
        return cs
    }

    /**
     * Execute an HTTP GET request and parse the response as a JSON tree.
     *
     * Request behavior:
     * - Query parameters from [params] are appended to [uri].
     * - A shared user-agent is always sent.
     * - [headers], when present, are added to the request and may override defaults.
     * - The shared client is reused unless a non-default timeout requires a derived client.
     *
     * Response behavior:
     * - The body is decoded using [decodeByContentEncoding].
     * - Text decoding uses the charset extracted from `Content-Type`, or UTF-8 as fallback.
     * - The final text is parsed with [MAPPER.readTree].
     * - HTTP status codes `>= 400` throw [IOException], using a best-effort message extracted from
     *   the JSON body when possible.
     *
     * @param uri base request URI
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed JSON root node
     * @throws IOException if the URL is invalid, the request fails, the response is not valid JSON,
     * or the server returns an HTTP error status
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun getAny(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonNode {
        val t0 = System.currentTimeMillis()
        log.debug("Passed in a JSON HTTP request. uri={} param={} headers={}", uri, params, headers)

        val base = uri.toString().toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $uri")

        val httpUrl = base.newBuilder().apply {
            params?.forEach { (k, v) ->
                if (v != null) addQueryParameter(k, v)
            }
        }.build()

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .header("User-Agent", UA)
            .apply { headers?.forEach { (k, v) -> header(k, v) } }
            .build()

        val callClient =
            if (readTimeoutMs == DEFAULT_READ_TIMEOUT) client
            else client.newBuilder()
                .callTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()

        callClient.newCall(request).execute().use { resp ->
            val code = resp.code

            val encoding = resp.header("Content-Encoding").orEmpty().trim().lowercase()
            val contentType = resp.header("Content-Type")
            val charsetName = extractCharset(contentType)

            val plain = decodeByContentEncoding(resp.body.bytes(), encoding)

            val body = try {
                String(plain, Charset.forName(charsetName))
            } catch (_: Exception) {
                String(plain, StandardCharsets.UTF_8)
            }

            val root: JsonNode = try {
                MAPPER.readTree(body)
            } catch (e: Exception) {
                if (code >= 400) throw IOException("HTTP $code")
                throw IOException("Response is not valid JSON", e)
            }

            if (code >= 400) throw IOException(extractErrorMessage(root, code))

            log.debug(
                "Result received after {} seconds: {}",
                (System.currentTimeMillis() - t0) / 1000,
                MAPPER.writeValueAsString(root).truncate()
            )
            return root
        }
    }

    /**
     * String-based overload of [getAny].
     *
     * @param uri request URI as text
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed JSON root node
     * @throws IOException if URI parsing fails, the request fails, or the response cannot be
     * handled as valid JSON
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun getAny(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonNode = getAny(URI(uri), params, headers, readTimeoutMs)

    /**
     * Execute an HTTP GET request and require the JSON root to be an object.
     *
     * This is a typed convenience wrapper over [getAny]. It should be used when the remote API is
     * expected to return an object root such as `{ ... }`.
     *
     * @param uri base request URI
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed root object node
     * @throws IOException if the request fails or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root exists but is not an object
     */
    @Throws(IOException::class)
    fun getObject(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): ObjectNode {
        val root = getAny(uri, params, headers, readTimeoutMs)
        return root.asObjectOpt().orElseThrow {
            IllegalArgumentException("Response JSON is not an object (it is ${typeOf(root)})")
        }
    }

    /**
     * String-based overload of [getObject].
     *
     * @param uri request URI as text
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed root object node
     * @throws IOException if the request fails or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root exists but is not an object
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getObject(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): ObjectNode = getObject(URI(uri), params, headers, readTimeoutMs)

    /**
     * Execute an HTTP GET request and require the JSON root to be an array.
     *
     * This is a typed convenience wrapper over [getAny]. It should be used when the remote API is
     * expected to return an array root such as `[ ... ]`.
     *
     * @param uri base request URI
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed root array node
     * @throws IOException if the request fails or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root exists but is not an array
     */
    @Throws(IOException::class)
    fun getArray(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): ArrayNode {
        val root = getAny(uri, params, headers, readTimeoutMs)
        return root.asArrayOpt().orElseThrow {
            IllegalArgumentException("Response JSON is not an array (it is ${typeOf(root)})")
        }
    }

    /**
     * String-based overload of [getArray].
     *
     * @param uri request URI as text
     * @param params optional query parameters; entries with `null` values are skipped
     * @param headers optional additional request headers; defaults to [DEFAULT_HEADERS]
     * @param readTimeoutMs per-call timeout in milliseconds
     * @return parsed root array node
     * @throws IOException if the request fails or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root exists but is not an array
     */
    @Throws(IOException::class)
    fun getArray(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): ArrayNode {
        return getArray(URI(uri), params, headers, readTimeoutMs)
    }

    /**
     * Build a URL query string from a key/value map.
     *
     * The resulting string starts with `?` when at least one entry exists; otherwise an empty
     * string is returned. Keys and values are encoded with UTF-8 via [URLEncoder].
     *
     * This helper is primarily meant for logging, debugging, or code paths that need a textual
     * query string. The main request methods do **not** rely on it; they use OkHttp's URL builder.
     *
     * @param q map of query parameters
     * @return encoded query string, or an empty string if [q] is null/empty
     */
    @JvmStatic
    fun buildQuery(q: Map<String, String>?): String {
        if (q.isNullOrEmpty()) return ""
        return q.entries.joinToString(separator = "&", prefix = "?", postfix = "") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
    }

    /**
     * URL-encode a single query-string component using UTF-8.
     */
    private fun encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    /**
     * Extract a human-readable error message from a JSON error response.
     *
     * The method looks for common API error fields in the following order:
     * - `message`
     * - `error`
     * - `detail`
     *
     * If no usable value is found, a generic `HTTP {code}` message is returned.
     *
     * @param root parsed response body, if parsing succeeded
     * @param code HTTP status code
     * @return best-effort error message
     */
    private fun extractErrorMessage(root: JsonNode?, code: Int): String {
        val obj = root?.asObjectOpt()?.orElse(null)
        if (obj != null) {

            if (obj.has("message") && !obj["message"].isNull) {
                try {
                    val m = obj["message"].asString()
                    if (m.isNotBlank()) return m
                } catch (_: Exception) {
                }
            }

            for (k in arrayOf("error", "detail")) {
                if (obj.has(k) && !obj[k].isNull) {
                    try {
                        val m = obj[k].asString()
                        if (m.isNotBlank()) return m
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return "HTTP $code"
    }

    /**
     * Return a small human-readable description of a JSON node category.
     *
     * This helper is used for exception messages when callers request a specific JSON root type.
     */
    private fun typeOf(el: JsonNode?): String = when {
        el == null -> "null"
        el.isObject -> "object"
        el.isArray -> "array"
        el.isValueNode -> "value node"
        el.isNull -> "null"
        else -> "unknown"
    }

}
