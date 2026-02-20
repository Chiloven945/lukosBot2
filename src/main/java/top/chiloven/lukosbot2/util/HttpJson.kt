package top.chiloven.lukosbot2.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.HttpJson.getAny
import top.chiloven.lukosbot2.util.HttpJson.getArray
import top.chiloven.lukosbot2.util.HttpJson.getObject
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * A small HTTP GET + JSON parsing utility.
 *
 * This is a Kotlin rewrite of the original Java [HttpJson] and keeps the same public API shape:
 *  - Multiple overloads for [getAny]/[getObject]/[getArray] accepting [URI] or [String]
 *  - Optional additional headers
 *  - Configurable read timeout per request
 *  - Automatic decoding for `Content-Encoding`: `identity`, `gzip`, `deflate`
 *  - Charset detection from `Content-Type` header (defaults to UTF-8)
 *  - HTTP errors (status &gt;= 400) raise [IOException] with best-effort message extraction
 *
 * Note: This object is stateful only for sharing a single [HttpClient] instance.
 */
object HttpJson {
    private const val DEFAULT_READ_TIMEOUT: Int = 10_000

    private val DEFAULT_HEADERS: Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Encoding" to "identity"
    )
    private const val UA: String = "Mozilla/5.0 (compatible; ${Constants.UA})"

    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedKey: String? = null

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

    private fun proxyOrNull(): ProxyConfigProp? = try {
        SpringBeans.getBean(ProxyConfigProp::class.java)
    } catch (_: Throwable) {
        null
    }

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
     * Sends a GET request and parses the JSON response.
     *
     * @param uri the request URI
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun getAny(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonElement {
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

            val root: JsonElement = try {
                JsonParser.parseString(body)
            } catch (e: Exception) {
                if (code >= 400) throw IOException("HTTP $code")
                throw IOException("Response is not valid JSON", e)
            }

            if (code >= 400) throw IOException(extractErrorMessage(root, code))
            return root
        }
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri the request URI string
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun getAny(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonElement = getAny(URI(uri), params, headers, readTimeoutMs)

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri the request URI
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    @Throws(IOException::class)
    fun getObject(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonObject {
        val root = getAny(uri, params, headers, readTimeoutMs)
        if (root.isJsonObject) return root.asJsonObject
        throw IllegalArgumentException("Response JSON is not an object (it is ${typeOf(root)})")
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri the request URI string
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getObject(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonObject = getObject(URI(uri), params, headers, readTimeoutMs)

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri the request URI
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    @Throws(IOException::class)
    fun getArray(
        uri: URI,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonArray {
        val root = getAny(uri, params, headers, readTimeoutMs)
        if (root.isJsonArray) return root.asJsonArray
        throw IllegalArgumentException("Response JSON is not an array (it is ${typeOf(root)})")
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri the request URI string
     * @param headers additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    @Throws(IOException::class)
    fun getArray(
        uri: String,
        params: Map<String, String?>? = null,
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonArray {
        return getArray(URI(uri), params, headers, readTimeoutMs)
    }

    /**
     * Builds a query string from a map.
     *
     * @param q query params
     * @return query string starting with '?', or empty string
     */
    @JvmStatic
    fun buildQuery(q: Map<String, String>?): String {
        if (q.isNullOrEmpty()) return ""
        return q.entries.joinToString(separator = "&", prefix = "?", postfix = "") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    private fun extractErrorMessage(root: JsonElement?, code: Int): String {
        if (root != null && root.isJsonObject) {
            val obj = root.asJsonObject

            if (obj.has("message") && !obj["message"].isJsonNull) {
                try {
                    val m = obj["message"].asString
                    if (m.isNotBlank()) return m
                } catch (_: Exception) {
                }
            }

            for (k in arrayOf("error", "detail")) {
                if (obj.has(k) && !obj[k].isJsonNull) {
                    try {
                        val m = obj[k].asString
                        if (m.isNotBlank()) return m
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return "HTTP $code"
    }

    private fun typeOf(el: JsonElement?): String = when {
        el == null -> "null"
        el.isJsonObject -> "object"
        el.isJsonArray -> "array"
        el.isJsonPrimitive -> "primitive"
        el.isJsonNull -> "null"
        else -> "unknown"
    }
}