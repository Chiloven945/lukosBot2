package top.chiloven.lukosbot2.util.feature

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.model.ContentData
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.time.Duration

object WebToMarkdown {
    private const val USER_AGENT = "Mozilla/5.0 (compatible; LukosBot/${Constants.VERSION})"

    private val TIMEOUT_MS: Int = Duration.ofSeconds(15).toMillis().toInt()
    private val html2md: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()
    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\r\n\t]""")

    @Throws(Exception::class)
    @JvmStatic
    fun fetchAndConvertWithSelectors(
        url: String,
        titleSelector: String?,
        contentSelectorsCsv: String?,
        defaultTitleBase: String?
    ): ContentData {
        val proxy = SpringBeans.getBean(ProxyConfigProp::class.java)

        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)

        val javaProxy: Proxy = proxy.toJavaProxy()
        if (proxy.isEnabled && javaProxy != Proxy.NO_PROXY) {
            conn.proxy(javaProxy)
        }

        val doc = conn.get()

        val title = resolveTitle(docTitleFallback = defaultTitleBase, doc = doc, titleSelector = titleSelector)
        val filename = "${sanitizeFilename(title, fallback = defaultTitleBase ?: "page")}.md"

        val contentEl = selectFirstByCsv(doc, contentSelectorsCsv)
        val html = (contentEl?.html() ?: doc.html())

        val md = html2md.convert(html)
        val bytes = md.toByteArray(StandardCharsets.UTF_8)

        return ContentData(filename, "text/markdown; charset=utf-8", bytes)
    }

    @Throws(Exception::class)
    @JvmStatic
    fun fetchWikipediaMarkdown(url: String): ContentData =
        fetchAndConvertWithSelectors(url, "h1#firstHeading", "#content", "wikipedia")

    private fun resolveTitle(docTitleFallback: String?, doc: org.jsoup.nodes.Document, titleSelector: String?): String {
        val fallback = docTitleFallback?.takeIf { it.isNotBlank() } ?: "page"
        val selector = titleSelector?.trim().orEmpty()
        if (selector.isEmpty()) return fallback

        val el = doc.selectFirst(selector)
        val text = el?.text()?.trim().orEmpty()
        return text.ifEmpty { fallback }
    }

    private fun selectFirstByCsv(doc: org.jsoup.nodes.Document, csv: String?): Element? {
        val raw = csv?.trim().orEmpty()
        if (raw.isEmpty()) return null

        return raw.splitToSequence(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstNotNullOfOrNull { sel -> doc.selectFirst(sel) }
    }

    private fun sanitizeFilename(raw: String?, fallback: String): String {
        val base = raw?.takeIf { it.isNotBlank() } ?: fallback
        val safe = base.replace(ILLEGAL_FILENAME_CHARS, "_").trim()
        return safe.ifEmpty { fallback }
    }
}
