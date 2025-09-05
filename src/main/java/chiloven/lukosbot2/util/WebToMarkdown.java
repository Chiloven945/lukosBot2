package chiloven.lukosbot2.util;

import chiloven.lukosbot2.model.ContentData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.charset.StandardCharsets;

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
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                .timeout((int) java.time.Duration.ofSeconds(15).toMillis())
                .get();

        // 标题 -> 文件名
        String title = "wikipedia";
        var h1 = doc.selectFirst("h1#firstHeading");
        if (h1 != null) title = h1.text();
        String filename = sanitizeFilename(title) + ".md";

        // 主体内容（按你原来的选择器）
        String html = doc.selectFirst("#content") != null
                ? java.util.Objects.requireNonNull(doc.selectFirst("#content")).html()
                : doc.html();

        String md = com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter.builder().build().convert(html);
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        return new ContentData(filename, "text/markdown; charset=utf-8", bytes);
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "wikipedia";
        String safe = raw.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        return safe.isEmpty() ? "wikipedia" : safe;
    }

}
