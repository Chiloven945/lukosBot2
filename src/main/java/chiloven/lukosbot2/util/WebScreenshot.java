package chiloven.lukosbot2.util;

import chiloven.lukosbot2.commands.WikiCommand;
import chiloven.lukosbot2.model.ContentData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.openqa.selenium.*;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WebScreenshot {
    private static final Logger log = LogManager.getLogger(WebScreenshot.class);

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
            driver.manage().window().setSize(new Dimension(1280, Objects.requireNonNull(height).intValue()));

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
     * Take a screenshot of the introductory section of a Wikipedia article.
     *
     * @param url the Wikipedia article URL
     * @return the screenshot as a byte array (PNG format)
     * @throws Exception if an error occurs or if the URL is not a Wikipedia link
     */
    public static ContentData screenshotWikipedia(String url) throws Exception {
        if (!WikiCommand.isWikipedia(url)) {
            throw new IllegalArgumentException("Not a Wikipedia URL: " + url);
        }

        WebDriver driver = null;
        try {
            log.info("Starting Wikipedia intro screenshot: {}", url);
            driver = createDriver();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
            driver.get(url);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Inject CSS to hide overlays that may block content
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

            String script = loadScript("js/wikiIntroMeasure.js");
            log.debug("Loaded measure script.");

            Number n1 = (Number) ((JavascriptExecutor) driver).executeScript(script);
            int h1 = toPx(n1, 1200);
            log.debug("First measured height = {}", h1);
            driver.manage().window().setSize(new Dimension(1920, h1));
            Thread.sleep(700);

            Number n2 = (Number) ((JavascriptExecutor) driver).executeScript(script);
            int h2 = toPx(n2, h1);
            int target = Math.max(h1, h2);
            log.debug("Second measured height = {}, final target = {}", h2, target);
            if (target > h1) {
                driver.manage().window().setSize(new Dimension(1920, target));
                Thread.sleep(400);
            }

            log.info("Wikipedia intro screenshot completed.");
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            byte[] jpg = pngToJpg(png, 0.9f);

            // 取条目标题作为文件名
            String title = "wikipedia";
            try {
                var doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                        .timeout(10_000)
                        .get();
                var h1Tag = doc.selectFirst("h1#firstHeading");
                if (h1Tag != null) title = h1Tag.text();
            } catch (Exception ignore) {
            }

            String filename = sanitizeFilename(title) + ".jpg";
            return new ContentData(filename, "image/jpeg", jpg);
        } catch (Exception e) {
            log.error("Wikipedia intro screenshot failed for {}", url, e);
            throw e;
        } finally {
            if (driver != null) try {
                driver.quit();
                log.debug("WebDriver quit successfully (wiki intro)");
            } catch (Exception ignored) {
            }
        }
    }

    // ====== Internal Utils ======
    private static WebDriver createDriver() {
        String chromeBin = null;

        // Environment variable
        String env = System.getenv("CHROME_BIN");
        if (env != null && new File(env).isFile()) {
            chromeBin = env;
        } else {
            // Common install locations on Windows
            List<String> candidates = new ArrayList<>();
            String local = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");

            if (programFiles != null) candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
            if (programFilesX86 != null) candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
            if (local != null) candidates.add(local + "\\Google\\Chrome\\Application\\chrome.exe");

            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                candidates.add(userHome + "\\.cache\\selenium\\chrome\\win64\\chrome.exe");
                candidates.add(userHome + "\\AppData\\Local\\ms-playwright\\chrome\\chrome-win\\chrome.exe");
            }

            for (String c : candidates) {
                if (new File(c).isFile()) {
                    chromeBin = c;
                    break;
                }
            }
        }

        if (chromeBin != null) {
            ChromeOptions co = new ChromeOptions();
            co.addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--window-size=1920,1080",
                    "--force-device-scale-factor=3"
            );
            co.setBinary(chromeBin);
            return new ChromeDriver(co);
        } else {
            EdgeOptions eo = new EdgeOptions();
            eo.addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--window-size=1920,1080",
                    "--force-device-scale-factor=3"
            );
            return new EdgeDriver(eo);
        }
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

    /**
     * Convert PNG bytes to JPG bytes with specified quality.
     * Fills transparent areas with a white background.
     *
     * @param pngBytes the PNG image bytes
     * @param quality  the JPEG quality (0.0 to 1.0)
     * @return the JPG image bytes
     * @throws Exception if conversion fails
     */
    private static byte[] pngToJpg(byte[] pngBytes, float quality) throws Exception {
        var src = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (src == null) throw new IllegalArgumentException("Invalid screenshot image");
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();

        try (var baos = new ByteArrayOutputStream()) {
            var writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (var ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                var param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        }
    }


}
