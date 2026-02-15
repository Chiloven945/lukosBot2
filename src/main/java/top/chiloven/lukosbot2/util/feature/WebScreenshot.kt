package top.chiloven.lukosbot2.util.feature

import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.edge.EdgeOptions
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.model.ContentData
import top.chiloven.lukosbot2.util.ImageUtils
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.Duration

object WebScreenshot {
    private val log = LogManager.getLogger(WebScreenshot::class.java)

    private const val USER_AGENT = "Mozilla/5.0 (compatible; ${Constants.UA})"
    private val PAGE_LOAD_TIMEOUT: Duration = Duration.ofSeconds(20)

    private const val FULLPAGE_WIDTH = 1080
    private const val FULLPAGE_MAX_HEIGHT = 8000

    private const val INTRO_WIDTH = 1380
    private const val INTRO_FALLBACK_HEIGHT = 1200

    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\r\n\t]""")

    /**
     * Take a full-page screenshot of the given URL.
     *
     * @param url the URL to screenshot
     * @return the screenshot as a byte array (PNG format)
     * @throws Exception if an error occurs
     */
    @Throws(Exception::class)
    @JvmStatic
    fun screenshotFullPage(url: String): ByteArray {
        log.info("Starting full-page screenshot: {}", url)

        createDriver().useDriver { driver ->
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT)
            driver.get(url)

            val height = driver.jsNumber(
                "return Math.min(document.body.scrollHeight || 800, $FULLPAGE_MAX_HEIGHT);",
                fallback = 800
            )

            driver.manage().window().size = Dimension(FULLPAGE_WIDTH, height)
            Thread.sleep(500)

            log.info("Full-page screenshot completed.")
            return (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
        }
    }

    /**
     * Wikipedia intro screenshot (delegates to MediaWiki core).
     */
    @Throws(Exception::class)
    @JvmStatic
    fun screenshotWikipedia(url: String): ContentData =
        screenshotMediaWiki(url, defaultFileBase = "wikipedia")

    /**
     * Minecraft Wiki intro screenshot (delegates to MediaWiki core).
     */
    @Throws(Exception::class)
    @JvmStatic
    fun screenshotMcWiki(url: String): ContentData =
        screenshotMediaWiki(url, defaultFileBase = "mcwiki")

    /**
     * Core intro screenshot for MediaWiki-family sites (e.g., Wikipedia, Minecraft Wiki).
     * Reuses the same DOM measurement & CSS-injection logic as Wikipedia.
     *
     * @param url             target article URL
     * @param defaultFileBase default filename base when title cannot be fetched (e.g., "wikipedia" / "mcwiki")
     * @return ContentData containing JPEG bytes, mime, and a safe filename
     */
    @Throws(Exception::class)
    private fun screenshotMediaWiki(url: String, defaultFileBase: String): ContentData {
        log.info("Starting MediaWiki intro screenshot: {}", url)

        createDriver().useDriver { driver ->
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT)
            driver.get(url)

            val js = driver as JavascriptExecutor

            log.debug("Injecting style to hide overlays")
            js.executeScript(
                """
                const hide = document.createElement('style');
                hide.innerHTML = `
                  .uls-menu, .uls-dialog, .vector-sticky-header,
                  .mw-banner-ct-container, .centralNotice { display:none !important; }
                `;
                document.documentElement.appendChild(hide);
                window.scrollTo(0, 0);
                """.trimIndent()
            )

            // Measure lead height twice for stability
            val script = loadScript("js/wikiIntroMeasure.js")
            log.debug("Loaded measure script.")

            val h1 = driver.jsNumber(script, fallback = INTRO_FALLBACK_HEIGHT)
            log.debug("First measured height = {}", h1)
            driver.manage().window().size = Dimension(INTRO_WIDTH, h1)
            Thread.sleep(700)

            val h2 = driver.jsNumber(script, fallback = h1)
            val target = maxOf(h1, h2)
            log.debug("Second measured height = {}, final target = {}", h2, target)

            if (target > h1) {
                driver.manage().window().size = Dimension(INTRO_WIDTH, target)
                Thread.sleep(400)
            }

            val png = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
            val jpg = ImageUtils.pngToJpg(png)

            val title = fetchMediaWikiTitle(url, defaultFileBase)
            val filename = "${sanitizeFilename(title, fallback = defaultFileBase)}.jpg"

            log.info("MediaWiki intro screenshot completed.")
            return ContentData(filename, "image/jpeg", jpg)
        }
    }

    private fun createDriver(): WebDriver {
        val proxy: ProxyConfigProp = SpringBeans.getBean(ProxyConfigProp::class.java)

        normalizeProxySystemProps()

        val chromeBin = detectChrome()
        return if (chromeBin != null) {
            val co = ChromeOptions().apply {
                addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--window-size=1920,1080",
                    "--force-device-scale-factor=3"
                )

                proxy.chromiumProxyArg()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { addArguments(it) }

                setBinary(chromeBin)
            }
            ChromeDriver(co)
        } else {
            val eo = EdgeOptions().apply {
                addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--window-size=1920,1080",
                    "--force-device-scale-factor=3"
                )

                proxy.toSeleniumProxy()
                    ?.takeIf { isValidSeleniumProxy(it) }
                    ?.let { setProxy(it) }
            }
            EdgeDriver(eo)
        }
    }

    private fun detectChrome(): String? {
        val env = System.getenv("CHROME_BIN")
        if (!env.isNullOrBlank() && File(env).isFile) {
            log.debug("Using CHROME_BIN from environment: {}", env)
            return env
        }

        val candidates = mutableListOf<String>()

        addCandidate(candidates, System.getenv("ProgramFiles"), "Google", "Chrome", "Application", "chrome.exe")
        addCandidate(candidates, System.getenv("ProgramFiles(x86)"), "Google", "Chrome", "Application", "chrome.exe")
        addCandidate(candidates, System.getenv("LOCALAPPDATA"), "Google", "Chrome", "Application", "chrome.exe")

        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank()) {
            addCandidate(candidates, home, ".cache", "selenium", "chrome", "win64", "chrome.exe")
            addCandidate(candidates, home, "AppData", "Local", "ms-playwright", "chrome", "chrome-win", "chrome.exe")
        }

        candidates.firstOrNull { File(it).isFile }?.let {
            log.debug("Detected Chrome binary: {}", it)
            return it
        }

        log.warn("Chrome binary not found in common locations.")
        return null
    }

    private fun addCandidate(list: MutableList<String>, base: String?, vararg parts: String) {
        if (base.isNullOrBlank()) return
        list += Paths.get(base, *parts).toString()
    }

    private fun normalizeProxySystemProps() {
        val keys = arrayOf(
            "http.proxyHost", "https.proxyHost",
            "http.proxyPort", "https.proxyPort",
            "http.nonProxyHosts"
        )
        for (k in keys) {
            val v = System.getProperty(k)
            if (v != null && v.isBlank()) {
                System.clearProperty(k)
                log.debug("Cleared blank system property: {}", k)
            }
        }
    }

    private fun isValidSeleniumProxy(p: Proxy): Boolean {
        val hp = p.httpProxy
        val sp = p.sslProxy
        val pac = p.proxyAutoconfigUrl
        return !hp.isNullOrBlank() || !sp.isNullOrBlank() || !pac.isNullOrBlank()
    }

    private fun WebDriver.jsNumber(script: String, fallback: Int): Int {
        val v = (this as JavascriptExecutor).executeScript(script)
        return toPx(v, fallback)
    }

    private fun toPx(v: Any?, fallback: Int): Int {
        val num = v as? Number ?: return fallback
        val d = num.toDouble()
        return if (d.isFinite()) kotlin.math.max(1, kotlin.math.round(d).toInt()) else fallback
    }

    private fun loadScript(resource: String): String {
        val inStream = WebScreenshot::class.java.classLoader.getResourceAsStream(resource)
            ?: throw IllegalStateException("Script not found: $resource")
        inStream.use { input ->
            return String(input.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private fun fetchMediaWikiTitle(url: String, fallback: String): String {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get()

            doc.selectFirst("h1#firstHeading")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (_: IOException) {
            fallback
        }
    }

    private fun sanitizeFilename(raw: String?, fallback: String): String {
        val base = raw?.takeIf { it.isNotBlank() } ?: fallback
        val safe = base.replace(ILLEGAL_FILENAME_CHARS, "_").trim()
        return safe.ifEmpty { fallback }
    }

    private inline fun <T> WebDriver.useDriver(block: (WebDriver) -> T): T {
        try {
            return block(this)
        } catch (e: Exception) {
            log.error("Web screenshot failed.", e)
            throw e
        } finally {
            try {
                quit()
                log.debug("WebDriver quit successfully.")
            } catch (_: Exception) {
            }
        }
    }
}
