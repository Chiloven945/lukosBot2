package top.chiloven.lukosbot2.commands.impl.wikis

import top.chiloven.lukosbot2.commands.IBotCommand
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Common helpers for commands that behave like “wiki” lookups.
 * 
 * 
 * This interface provides reusable logic for:
 * 
 *  * Parsing an optional language prefix in user input (for example `"en:Article"`)
 *  * Normalizing loose input (raw URL or title) into a full article URL
 *  * Checking whether a given URL belongs to the expected wiki domain
 *  * Converting a title into a URL-safe path segment with proper encoding
 * 
 * 
 * 
 * Concrete implementations only need to define:
 * 
 *  * Which default language code to use (for example `"en"`)
 *  * What path prefix is used before the encoded title (for example `"/wiki/"` or `"/w/"`)
 *  * What domain root is expected (for example `"wikipedia.org"` or `"minecraft.wiki"`)
 * 
 * 
 * 
 * The common flow for a wiki-like command is usually:
 * 
 *  1. Read user input (may be a full URL or a raw title, possibly prefixed with a language code)
 *  1. Call [.normalize] to convert input into a canonical article URL
 *  1. Optionally verify that the URL belongs to this wiki using [.isNot]
 *  1. Return or fetch content based on the normalized URL
 * 
 * 
 * 
 * Example inputs handled by this helper:
 * 
 *  * `"en:Village"` → `https://en.&lt;domainRoot&gt;&lt;pathPrefix&gt;Village`
 *  * `"zh-cn: 村庄"` → encoded and prefixed with `zh-cn.`
 *  * `"村庄"` → uses [.defaultLang] as the language
 *  * `"https://en.&lt;domainRoot&gt;/wiki/Village"` → returned as-is
 * 
 * 
 * @author Chiloven945
 */
interface IWikiishCommand : IBotCommand {
    // TODO: change to the language in the application.yml

    /**
     * Returns the default language code used when input does not contain an explicit
     * language prefix.
     *
     * Typical examples include `"en"`, `"zh-cn"` or `"ja"`.
     * Implementations can use any tag understood by the target site.
     *
     * @return default language code (never `null`)
     */
    fun defaultLang(): String

    /**
     * Returns the root domain for this wiki site (without language subdomain).
     *
     * Examples:
     *
     *  * `"wikipedia.org"`
     *  * `"minecraft.wiki"`
     *  * `"example.com"`
     *
     * The language portion is handled separately and prepended to this root domain.
     *
     * @return root domain name (for example `"wikipedia.org"`)
     */
    fun domainRoot(): String

    /**
     * Returns the path prefix that appears before the encoded article title.
     *
     * Different wiki engines use different paths. For example:
     *
     *  * Classic MediaWiki: `"/wiki/"`
     *  * Minecraft Wiki: `"/w/"`
     *  * Custom setups may even use `"/"` or other structures
     *
     * This value is concatenated after the domain and before the encoded title.
     *
     * @return path prefix used in article URLs (for example `"/wiki/"`)
     */
    fun pathPrefix(): String

    /**
     * Normalizes user input into a canonical article URL.
     * 
     * 
     * This method accepts either:
     * 
     *  * A full URL starting with `http://` or `https://`, which is returned as-is
     *  * A raw title, optionally prefixed with `"xx:"` where `xx` is a language code
     * 
     * When a language prefix is present, it is extracted via
     * [.splitLangPrefixedTitle]; otherwise [.defaultLang] is used.
     * 
     * @param linkOrTitle raw user input, may be `null`, a URL or a title
     * @return a full URL string that can be opened or further processed
     */
    fun normalize(linkOrTitle: String?): String {
        val s = linkOrTitle?.trim().orEmpty()
        if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) return s

        val lt = splitLangPrefixedTitle(s, defaultLang())
        val lang = lt.lang.ifBlank { defaultLang() }
        val title = sanitizeTitle(lt.title)
        return titleToUrl(lang, title)
    }

    /**
     * Splits an optional language prefix from input of the form `"xx:Title"`.
     * 
     * Rules:
     * 
     *  * If the input contains `':'` at position 1–6 (inclusive), the part
     * before the colon is treated as a potential language tag.
     *  * The candidate language tag must match `[A-Za-z-]{2,6}`; otherwise
     * it is ignored.
     *  * If a valid language tag is found, it is returned along with the remainder
     * of the string as the title.
     *  * If no valid tag is found, `fallbackLang` is used and the full input
     * is treated as the title.
     * 
     * Some examples (assuming `fallbackLang = "en"`):
     * 
     *  * `"en:Village"` → `("en", "Village")`
     *  * `"zh-cn: 村庄"` → `("zh-cn", "村庄")`
     *  * `"Village"` → `("en", "Village")`
     * 
     * @param input        raw input string, may be `null`
     * @param fallbackLang language code to use when no valid prefix is found
     * @return a [LangTitle] record containing the chosen language and remaining title
     */
    fun splitLangPrefixedTitle(input: String?, fallbackLang: String?): LangTitle {
        var s = input?.trim().orEmpty()
        var lang = fallbackLang.orEmpty()

        val idx = s.indexOf(':')
        if (idx in 1..6) {
            val maybe = s.substring(0, idx)
            if (maybe.matches(LANG_TAG_RE)) {
                lang = maybe
                s = s.substring(idx + 1).trim()
            }
        }
        return LangTitle(lang, s)
    }

    /**
     * Builds a full article URL from a language code and a page title.
     * 
     * 
     * This default implementation:
     * 
     *  1. Sanitizes the title via [.sanitizeTitle] (spaces → underscores)
     *  1. URL-encodes the sanitized title using UTF-8
     *  1. Creates a URL in the form:
     * `https://<lang>.<domainRoot()><pathPrefix()><encodedTitle>`
     * 
     * 
     * 
     * Implementations may override this method if a wiki has custom URL rules.
     * 
     * @param lang  language code to use in the subdomain (for example `"en"`)
     * @param title raw article title as provided by the user
     * @return full URL string pointing to the article
     */
    fun titleToUrl(lang: String, title: String?): String {
        val sanitized = sanitizeTitle(title)
        val enc = runCatching { URLEncoder.encode(sanitized, StandardCharsets.UTF_8) }
            .getOrElse { sanitized }
        return "https://${lang}.${domainRoot()}${pathPrefix()}$enc"
    }

    /**
     * Performs basic sanitation on a wiki title.
     * 
     * The default implementation replaces spaces with underscores, mirroring
     * common wiki conventions. Implementations may override this if a wiki uses
     * a different normalization scheme.
     * 
     * @param title raw title, may be `null`
     * @return sanitized title, never `null`
     */
    fun sanitizeTitle(title: String?): String =
        title?.replace(' ', '_')?.trim().orEmpty()

    /**
     * Checks whether the given URL does **not** belong to a specific domain root.
     * 
     * 
     * The check is based on the host part of the URL. It returns `true` if:
     * 
     *  * the URL is malformed or cannot be parsed, or
     *  * the host does not equal `domain` and does not end with `"." + domain`
     * 
     * This method is intentionally conservative: invalid URLs are treated as "not belonging".
     * 
     * @param url    full URL string to test
     * @param domain expected root domain (for example `"wikipedia.org"`)
     * @return `true` if the URL is considered outside the given domain, `false` otherwise
     */
    fun isNot(url: String, domain: String = domainRoot()): Boolean {
        return try {
            val u = URI(url).toURL()
            val host = u.host?.lowercase(Locale.ROOT).orEmpty()
            !(host == domain || host.endsWith(".$domain"))
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Parsed a pair of language and title derived from an input string.
     * 
     * 
     * Typical sources are strings like `"en:Article"` or `"Article"`
     * (with fallback language applied).
     * 
     * @param lang  resolved language code
     * @param title resolved article title
     */
    data class LangTitle(val lang: String, val title: String)

    companion object {
        private val LANG_TAG_RE = Regex("""[A-Za-z-]{2,6}""")
    }
}
