package chiloven.lukosbot2.util;

import chiloven.lukosbot2.config.ProxyConfig;
import chiloven.lukosbot2.model.ContentData;
import chiloven.lukosbot2.support.SpringBeans;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class WebToMarkdown {
    private WebToMarkdown() {
    }

    /**
     * Fetch HTML content from a URL and convert it to Markdown.
     * Uses Jsoup for fetching and Flexmark for conversion.
     *
     * @param url the URL to fetch
     * @return the content in Markdown format as a byte array
     * @throws Exception if fetching or conversion fails
     */
    public static ContentData fetchAndConvert(String url) throws Exception {
        ProxyConfig proxy =
                SpringBeans.getBean(ProxyConfig.class);

        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                .timeout((int) Duration.ofSeconds(15).toMillis());

        Proxy p = proxy.toJavaProxy();
        if (proxy.isEnabled() && p != Proxy.NO_PROXY) {
            conn.proxy(p);
        }

        Document doc = conn.get();

        // 标题 -> 文件名
        String title = "wikipedia";
        var h1 = doc.selectFirst("h1#firstHeading");
        if (h1 != null) title = h1.text();
        String filename = sanitizeFilename(title) + ".md";

        // 主体内容
        String html = doc.selectFirst("#content") != null
                ? Objects.requireNonNull(doc.selectFirst("#content")).html()
                : doc.html();

        String md = FlexmarkHtmlConverter.builder().build().convert(html);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        return new ContentData(filename, "text/markdown; charset=utf-8", bytes);
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "wikipedia";
        String safe = raw.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        return safe.isEmpty() ? "wikipedia" : safe;
    }

}
