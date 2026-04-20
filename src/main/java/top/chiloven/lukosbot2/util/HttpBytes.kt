package top.chiloven.lukosbot2.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.Constants
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Lightweight HTTP GET + binary payload helper built on top of OkHttp.
 *
 * This utility exists for the common pattern where the project needs to fetch a remote file or
 * image as raw bytes and keep a small amount of response metadata alongside it.
 *
 * Typical usage is:
 *
 * 1. Perform an HTTP GET request for a binary resource.
 * 2. Read the response body as [ByteArray].
 * 3. Extract basic metadata such as media type and file name.
 * 4. Return everything as a small immutable DTO.
 *
 * ## Scope and design goals
 *
 * `HttpBytes` intentionally stays small and opinionated:
 *
 * - Only **GET** requests are supported.
 * - The response body is always fully buffered into memory.
 * - Proxy configuration is auto-discovered from Spring when available.
 * - Redirects are followed.
 * - A shared user-agent is attached to each request.
 *
 * It is meant for straightforward resource downloads used by platform adapters and command-side
 * integrations, not as a general HTTP abstraction layer.
 *
 * ## Connection reuse
 *
 * The object caches a single [OkHttpClient] instance and rebuilds it only when the effective proxy
 * configuration changes. This preserves connection pooling and keeps repeated downloads efficient.
 *
 * ## Returned metadata
 *
 * The helper extracts the following best-effort metadata from the response:
 *
 * - MIME type from the `Content-Type` header, without parameters
 * - file name from the `Content-Disposition` header when present
 * - otherwise, a fallback file name inferred from the request URL path
 *
 * ## Failure behavior
 *
 * - Transport problems raise [IOException].
 * - HTTP status codes outside the success range raise [IOException].
 * - Missing filename or MIME information is represented as `null` instead of causing failure.
 *
 * @author Chiloven945
 */
object HttpBytes {

    private val log = LogManager.getLogger(HttpBytes::class.java)

    /**
     * Default per-call timeout in milliseconds used by public request helpers unless the caller
     * explicitly overrides it.
     */
    private const val DEFAULT_TIMEOUT_MS = 30_000

    /**
     * Shared user-agent sent with every request.
     */
    private const val UA = "Mozilla/5.0 (compatible; ${Constants.UA})"

    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = 15_000,
        readTimeoutMs = DEFAULT_TIMEOUT_MS.toLong()
    )

    /**
     * Result of a binary download request.
     *
     * @property bytes full response body buffered into memory
     * @property mime MIME type extracted from `Content-Type`, without parameters, or `null` when
     * unavailable
     * @property fileName best-effort filename extracted from `Content-Disposition` or inferred from
     * the request URL, or `null` when unavailable
     */
    data class BytePayload(
        val bytes: ByteArray,
        val mime: String?,
        val fileName: String?
    )

    /**
     * Lazily initialized and proxy-aware [OkHttpClient].
     */
    private val client: OkHttpClient
        get() = clientCache.client

    /**
     * Execute an HTTP GET request and return the response body as raw bytes plus basic metadata.
     *
     * Request behavior:
     * - A shared user-agent is always sent.
     * - The shared client is reused unless a non-default timeout requires a derived client.
     * - Redirects are followed according to the cached client's configuration.
     *
     * Response behavior:
     * - The body is fully buffered into memory.
     * - MIME type is extracted from `Content-Type` without parameters.
     * - Filename is resolved from `Content-Disposition` first and URL path second.
     *
     * @param url request URL
     * @param timeoutMs per-call timeout in milliseconds
     * @return buffered response payload and extracted metadata
     * @throws IOException if the request fails or the server returns a non-success HTTP status
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun get(url: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): BytePayload {
        val callClient = if (timeoutMs == DEFAULT_TIMEOUT_MS) client else client.newBuilder()
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()

        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", UA)
            .build()

        callClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} while fetching binary: $url")
            }
            val mime = resp.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.ifBlank { null }
            val fileName = parseFileName(resp.header("Content-Disposition")) ?: PathUtils.inferFileNameFromUrl(url)
            val bytes = resp.body.bytes()
            log.debug("Fetched {} bytes from {}", bytes.size, url)
            return BytePayload(bytes = bytes, mime = mime, fileName = fileName)
        }
    }

    /**
     * Try to extract a filename from a `Content-Disposition` header.
     *
     * The helper supports both `filename=` and RFC 5987 style `filename*=` parameters. When the
     * value cannot be decoded or no usable filename exists, `null` is returned.
     *
     * @param contentDisposition raw `Content-Disposition` header value
     * @return parsed filename, or `null` if unavailable
     */
    private fun parseFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val parts = contentDisposition.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            when {
                trimmed.startsWith("filename*=", ignoreCase = true) -> {
                    val raw = trimmed.substringAfter('=', "").trim().trim('"')
                    val encoded = raw.substringAfter("''", raw)
                    return runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }
                        .getOrNull()
                        ?.ifBlank { null }
                }

                trimmed.startsWith("filename=", ignoreCase = true) -> {
                    return trimmed.substringAfter('=', "").trim().trim('"').ifBlank { null }
                }
            }
        }
        return null
    }

}
