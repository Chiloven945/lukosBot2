package top.chiloven.lukosbot2.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.chiloven.lukosbot2.util.HttpJson.getAny
import top.chiloven.lukosbot2.util.HttpJson.getArray
import top.chiloven.lukosbot2.util.HttpJson.getObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
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

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonElement {
        try {
            val b = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(readTimeoutMs.toLong()))
                .GET()

            headers?.forEach { (k, v) -> b.header(k, v) }

            val resp: HttpResponse<ByteArray> = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
            val code = resp.statusCode()

            val encoding = resp.headers().firstValue("Content-Encoding").orElse("").trim().lowercase()
            val contentType = resp.headers().firstValue("Content-Type").orElse("")
            val charsetName = extractCharset(contentType)

            val raw = resp.body()
            val plain = decodeByContentEncoding(raw, encoding)

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
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Request interrupted", e)
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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonElement = getAny(URI(uri), headers, readTimeoutMs)

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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonObject {
        val root = getAny(uri, headers, readTimeoutMs)
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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonObject = getObject(URI(uri), headers, readTimeoutMs)

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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonArray {
        val root = getAny(uri, headers, readTimeoutMs)
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
        headers: Map<String, String>? = DEFAULT_HEADERS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
    ): JsonArray {
        return getArray(URI(uri), headers, readTimeoutMs)
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

    private fun encode(s: String): String {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
    }

    private fun extractErrorMessage(root: JsonElement?, code: Int): String {
        if (root != null && root.isJsonObject) {
            val obj = root.asJsonObject

            if (obj.has("message") && !obj["message"].isJsonNull) {
                try {
                    val m = obj["message"].asString
                    if (!m.isNullOrBlank()) return m
                } catch (_: Exception) {
                }
            }

            for (k in arrayOf("error", "detail")) {
                if (obj.has(k) && !obj[k].isJsonNull) {
                    try {
                        val m = obj[k].asString
                        if (!m.isNullOrBlank()) return m
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return "HTTP $code"
    }

    private fun typeOf(el: JsonElement?): String {
        if (el == null) return "null"
        return when {
            el.isJsonObject -> "object"
            el.isJsonArray -> "array"
            el.isJsonPrimitive -> "primitive"
            el.isJsonNull -> "null"
            else -> "unknown"
        }
    }
}