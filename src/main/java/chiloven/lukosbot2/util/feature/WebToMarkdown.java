package chiloven.lukosbot2.util.feature;

import chiloven.lukosbot2.config.ProxyConfigProp;
import chiloven.lukosbot2.model.ContentData;
import chiloven.lukosbot2.util.spring.SpringBeans;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WebToMarkdown {
    private WebToMarkdown() {
    }

    // WebToMarkdown.java —— 在类中追加以下方法
    public static ContentData fetchAndConvertWithSelectors(
            String url,
            String titleSelector,
            String contentSelectorsCsv,
            String defaultTitleBase
    ) throws Exception {
        var proxy = SpringBeans.getBean(ProxyConfigProp.class);
        var conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                .timeout((int) Duration.ofSeconds(15).toMillis());

        Proxy p = proxy.toJavaProxy();
        if (proxy.isEnabled() && p != Proxy.NO_PROXY) {
            conn.proxy(p);
        }

        Document doc = conn.get();

        // 标题
        String title = defaultTitleBase != null ? defaultTitleBase : "page";
        var h = (titleSelector == null || titleSelector.isBlank())
                ? null
                : doc.selectFirst(titleSelector);
        if (h != null && !h.text().isBlank()) {
            title = h.text().trim();
        }
        String filename = sanitizeFilename(title) + ".md";

        // 主体容器：按逗号分隔的多个 selector，择一使用
        Element contentEl = null;
        if (contentSelectorsCsv != null && !contentSelectorsCsv.isBlank()) {
            for (String selector : contentSelectorsCsv.split(",")) {
                selector = selector.trim();
                if (selector.isEmpty()) continue;
                Element cand = doc.selectFirst(selector);
                if (cand != null) {
                    contentEl = cand;
                    break;
                }
            }
        }
        String html = contentEl != null ? contentEl.html() : doc.html();

        String md = FlexmarkHtmlConverter.builder().build().convert(html);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        return new ContentData(filename, "text/markdown; charset=utf-8", bytes);
    }

    public static ContentData fetchWikipediaMarkdown(String url) throws Exception {
        return fetchAndConvertWithSelectors(url, "h1#firstHeading", "#content", "wikipedia");
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "wikipedia";
        String safe = raw.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        return safe.isEmpty() ? "wikipedia" : safe;
    }

}
