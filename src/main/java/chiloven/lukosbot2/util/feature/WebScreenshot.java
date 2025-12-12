package chiloven.lukosbot2.util.feature;

import chiloven.lukosbot2.config.ProxyConfig;
import chiloven.lukosbot2.model.ContentData;
import chiloven.lukosbot2.util.ImageUtils;
import chiloven.lukosbot2.util.spring.SpringBeans;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
public final class WebScreenshot {
    private static final ImageUtils iu = ImageUtils.getImageUtils();

    private WebScreenshot() {
    }

    /**
     * Take a full-page screenshot of the given URL.
     *
     * @param url the URL to screenshot
     * @return the screenshot as a byte array (PNG format)
     * @throws Exception if an error occurs
     */
    public static byte[] screenshotFullPage(String url) throws Exception {
        WebDriver driver = null;
        try {
            log.info("Starting full-page screenshot: {}", url);
            driver = createDriver();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            driver.get(url);

            // Page height may vary; limit to a max height to avoid issues
            Long height = (Long) ((JavascriptExecutor) driver).executeScript("return Math.min(document.body.scrollHeight || 800, 8000);");
            driver.manage().window().setSize(new Dimension(1080, Objects.requireNonNull(height).intValue()));

            Thread.sleep(500);
            log.info("Full-page screenshot completed.");
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.error("Full-page screenshot failed for {}", url, e);
            throw e;
        } finally {
            if (driver != null) try {
                driver.quit();
                log.debug("WebDriver quit successfully (full-page)");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Core intro screenshot for MediaWiki-family sites (e.g., Wikipedia, Minecraft Wiki).
     * Reuses the same DOM measurement & CSS-injection logic as Wikipedia.
     *
     * @param url             target article URL
     * @param defaultFileBase default filename base when title cannot be fetched (e.g., "wikipedia" / "mcwiki")
     * @return ContentData containing JPEG bytes, mime, and a safe filename
     */
    private static ContentData screenshotMediaWiki(String url, String defaultFileBase) throws Exception {
        WebDriver driver = null;
        try {
            log.info("Starting MediaWiki intro screenshot: {}", url);
            driver = createDriver();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            driver.get(url);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Hide sticky headers / banners that may obstruct the lead section
            log.debug("Injecting style to hide overlays");
            js.executeScript("""
                        const hide = document.createElement('style');
                        hide.innerHTML = `
                          .uls-menu, .uls-dialog, .vector-sticky-header,
                          .mw-banner-ct-container, .centralNotice { display:none !important; }
                        `;
                        document.documentElement.appendChild(hide);
                        window.scrollTo(0, 0);
                    """);

            // Measure lead height twice for stability
            String script = loadScript("js/wikiIntroMeasure.js");
            log.debug("Loaded measure script.");

            Number n1 = (Number) js.executeScript(script);
            int h1 = toPx(n1, 1200);
            log.debug("First measured height = {}", h1);
            driver.manage().window().setSize(new Dimension(1380, h1));
            Thread.sleep(700);

            Number n2 = (Number) js.executeScript(script);
            int h2 = toPx(n2, h1);
            int target = Math.max(h1, h2);
            log.debug("Second measured height = {}, final target = {}", h2, target);
            if (target > h1) {
                driver.manage().window().setSize(new Dimension(1380, target));
                Thread.sleep(400);
            }

            log.info("MediaWiki intro screenshot completed.");
            // Take PNG then convert to JPEG to reduce size (consistent with Wikipedia path)
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            byte[] jpg = iu.pngToJpg(png);

            // Try to fetch page title as filename; fallback to provided base
            String title = defaultFileBase;
            try {
                var doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                        .timeout(10_000)
                        .get();
                var h1Tag = doc.selectFirst("h1#firstHeading");
                if (h1Tag != null && !h1Tag.text().isBlank()) {
                    title = h1Tag.text().trim();
                }
            } catch (IOException ignore) {
            }

            String filename = sanitizeFilename(title) + ".jpg";
            return new ContentData(filename, "image/jpeg", jpg);
        } catch (Exception e) {
            log.error("MediaWiki intro screenshot failed for {}", url, e);
            throw e;
        } finally {
            if (driver != null) try {
                driver.quit();
                log.debug("WebDriver quit successfully (mediawiki intro)");
            } catch (Exception _) {
            }
        }
    }

    public static ContentData screenshotWikipedia(String url) throws Exception {
        return screenshotMediaWiki(url, "wikipedia");
    }

    /**
     * Minecraft Wiki intro screenshot (delegates to MediaWiki core).
     */
    public static ContentData screenshotMcWiki(String url) throws Exception {
        return screenshotMediaWiki(url, "mcwiki");
    }

    // ====== Internal Utils ======
    private static WebDriver createDriver() {
        ProxyConfig proxy = SpringBeans.getBean(ProxyConfig.class);

        // 清理“空字符串”的系统代理属性，避免 Selenium Manager 拼出 --proxy 但无值
        normalizeProxySystemProps();

        String chromeBin = detectChrome();
        if (chromeBin != null) {
            ChromeOptions co = new ChromeOptions();
            co.addArguments("--headless=new", "--disable-gpu", "--no-sandbox",
                    "--disable-dev-shm-usage", "--window-size=1920,1080",
                    "--force-device-scale-factor=3");
            // 只有在确实有有效参数时才传给 Chromium
            String arg = proxy.chromiumProxyArg();
            if (hasText(arg)) {
                co.addArguments(arg);
            }
            if (arg != null) co.addArguments(arg);
            co.setBinary(chromeBin);
            return new ChromeDriver(co);
        } else {
            EdgeOptions eo = new EdgeOptions();
            eo.addArguments("--headless=new", "--disable-gpu", "--no-sandbox",
                    "--disable-dev-shm-usage", "--window-size=1920,1080",
                    "--force-device-scale-factor=3");
            // 仅当 ProxyConfig 真的给出了有效地址时才设置代理；否则明确走 DIRECT
            org.openqa.selenium.Proxy sp = proxy.toSeleniumProxy();
            if (isValidSeleniumProxy(sp)) {
                eo.setProxy(sp);
            }
            return new EdgeDriver(eo);
        }
    }

    private static String detectChrome() {
        // 1. 优先环境变量
        String env = System.getenv("CHROME_BIN");
        if (env != null && new File(env).isFile()) {
            log.debug("Using CHROME_BIN from environment: {}", env);
            return env;
        }

        List<String> candidates = new ArrayList<>();

        // 2. 常见安装路径
        addCandidate(candidates, System.getenv("ProgramFiles"), "Google", "Chrome", "Application", "chrome.exe");
        addCandidate(candidates, System.getenv("ProgramFiles(x86)"), "Google", "Chrome", "Application", "chrome.exe");
        addCandidate(candidates, System.getenv("LOCALAPPDATA"), "Google", "Chrome", "Application", "chrome.exe");

        // 3. 用户目录下的一些常见缓存/安装路径
        String home = System.getProperty("user.home");
        if (home != null) {
            addCandidate(candidates, home, ".cache", "selenium", "chrome", "win64", "chrome.exe");
            addCandidate(candidates, home, "AppData", "Local", "ms-playwright", "chrome", "chrome-win", "chrome.exe");
        }

        // 4. 遍历候选
        for (String c : candidates) {
            if (new File(c).isFile()) {
                log.debug("Detected Chrome binary: {}", c);
                return c;
            }
        }
        log.warn("Chrome binary not found in common locations.");
        return null;
    }

    private static void addCandidate(List<String> list, String base, String... parts) {
        if (base == null || base.isBlank()) return;
        Path path = Paths.get(base, parts);
        list.add(path.toString());
    }


    /**
     * 清理空字符串系统代理属性，避免 Selenium Manager 误认为需要 --proxy 但没有值。
     */
    private static void normalizeProxySystemProps() {
        String[] keys = {
                "http.proxyHost", "https.proxyHost",
                "http.proxyPort", "https.proxyPort",
                "http.nonProxyHosts"
        };
        for (String k : keys) {
            String v = System.getProperty(k);
            if (v != null && v.isBlank()) {
                System.clearProperty(k);
                log.debug("Cleared blank system property: {}", k);
            }
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 只在代理字段确实有值时才视为有效代理
     */
    private static boolean isValidSeleniumProxy(org.openqa.selenium.Proxy p) {
        if (p == null) return false;
        String hp = p.getHttpProxy();
        String sp = p.getSslProxy();
        String pac = p.getProxyAutoconfigUrl();
        return hasText(hp) || hasText(sp) || hasText(pac);
    }


    /**
     * Turns a valid pixel height from a JavaScript number (Long or Double) returned by Selenium.
     *
     * @param v        the value to convert
     * @param fallback the fallback value if conversion fails
     * @return the pixel height as an integer
     */
    private static int toPx(Object v, int fallback) {
        if (v instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isFinite(d)) return Math.max(1, (int) Math.round(d));
        }
        return fallback;
    }

    /**
     * Load a script from the classpath.
     *
     * @param resource the resource path (e.g., "scripts/some_script.js")
     * @return the script content as a String
     * @throws Exception if the resource cannot be found or read
     */
    private static String loadScript(String resource) throws Exception {
        try (var in = WebScreenshot.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Script not found: " + resource);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Sanitize a filename by replacing illegal characters with underscores.
     * If the result is empty, return "wikipedia".
     *
     * @param raw the raw filename
     * @return the sanitized filename
     */
    private static String sanitizeFilename(String raw) {
        if (raw == null) return "wikipedia";
        String safe = raw.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        return safe.isEmpty() ? "wikipedia" : safe;
    }

}
