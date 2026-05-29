/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.util.feature

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.core.model.ContentData
import top.chiloven.lukosbot2.util.PathUtils.sanitizeFileName
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.time.Duration

object WebToMarkdown {

    private val USER_AGENT = "Mozilla/5.0 (compatible; ${Constants.UA})"

    private val TIMEOUT_MS: Int = Duration.ofSeconds(15).toMillis().toInt()
    private val html2md: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()

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
        if (proxy.enabled && javaProxy != Proxy.NO_PROXY) {
            conn.proxy(javaProxy)
        }

        val doc = conn.get()

        val title = resolveTitle(docTitleFallback = defaultTitleBase, doc = doc, titleSelector = titleSelector)
        val filename = "${sanitizeFileName(title, fallback = defaultTitleBase ?: "page")}.md"

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

}
