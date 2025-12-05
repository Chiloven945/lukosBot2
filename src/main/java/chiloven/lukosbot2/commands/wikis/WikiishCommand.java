package chiloven.lukosbot2.commands.wikis;

import chiloven.lukosbot2.commands.BotCommand;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Common helpers for commands that behave like “wiki” lookups.
 *
 * <p>This interface provides reusable logic for:
 * <ul>
 *     <li>Parsing an optional language prefix in user input (for example {@code "en:Article"})</li>
 *     <li>Normalizing loose input (raw URL or title) into a full article URL</li>
 *     <li>Checking whether a given URL belongs to the expected wiki domain</li>
 *     <li>Converting a title into a URL-safe path segment with proper encoding</li>
 * </ul>
 *
 * <p>Concrete implementations only need to define:
 * <ul>
 *     <li>Which default language code to use (for example {@code "en"})</li>
 *     <li>What path prefix is used before the encoded title (for example {@code "/wiki/"} or {@code "/w/"})</li>
 *     <li>What domain root is expected (for example {@code "wikipedia.org"} or {@code "minecraft.wiki"})</li>
 * </ul>
 *
 * <p>The common flow for a wiki-like command is usually:
 * <ol>
 *     <li>Read user input (may be a full URL or a raw title, possibly prefixed with a language code)</li>
 *     <li>Call {@link #normalize(String)} to convert input into a canonical article URL</li>
 *     <li>Optionally verify that the URL belongs to this wiki using {@link #isNot(String)}</li>
 *     <li>Return or fetch content based on the normalized URL</li>
 * </ol>
 *
 * <p>Example inputs handled by this helper:
 * <ul>
 *     <li>{@code "en:Village"} → {@code https://en.&lt;domainRoot&gt;&lt;pathPrefix&gt;Village}</li>
 *     <li>{@code "zh-cn: 村庄"} → encoded and prefixed with {@code zh-cn.}</li>
 *     <li>{@code "村庄"} → uses {@link #defaultLang()} as the language</li>
 *     <li>{@code "https://en.&lt;domainRoot&gt;/wiki/Village"} → returned as-is</li>
 * </ul>
 *
 * @author Chiloven945
 */
public interface WikiishCommand extends BotCommand {

    // TODO: change to the language in the application.yml

    /**
     * Normalizes user input into a canonical article URL.
     *
     * <p>This method accepts either:
     * <ul>
     *     <li>A full URL starting with {@code http://} or {@code https://}, which is returned as-is</li>
     *     <li>A raw title, optionally prefixed with {@code "xx:"} where {@code xx} is a language code</li>
     * </ul>
     *
     * <p>When a language prefix is present, it is extracted via
     * {@link #splitLangPrefixedTitle(String, String)}; otherwise {@link #defaultLang()} is used.</p>
     *
     * @param linkOrTitle raw user input, may be {@code null}, a URL or a title
     * @return a full URL string that can be opened or further processed
     */
    default String normalize(String linkOrTitle) {
        String s = linkOrTitle == null ? "" : linkOrTitle.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        LangTitle lt = splitLangPrefixedTitle(s, defaultLang());
        return titleToUrl(lt.lang(), sanitizeTitle(lt.title()));
    }

    /**
     * Splits an optional language prefix from input of the form {@code "xx:Title"}.
     *
     * <p>Rules:
     * <ul>
     *     <li>If the input contains {@code ':'} at position 1–6 (inclusive), the part
     *         before the colon is treated as a potential language tag.</li>
     *     <li>The candidate language tag must match {@code [A-Za-z-]{2,6}}; otherwise
     *         it is ignored.</li>
     *     <li>If a valid language tag is found, it is returned along with the remainder
     *         of the string as the title.</li>
     *     <li>If no valid tag is found, {@code fallbackLang} is used and the full input
     *         is treated as the title.</li>
     * </ul>
     *
     * <p>Some examples (assuming {@code fallbackLang = "en"}):</p>
     * <ul>
     *     <li>{@code "en:Village"} → {@code ("en", "Village")}</li>
     *     <li>{@code "zh-cn: 村庄"} → {@code ("zh-cn", "村庄")}</li>
     *     <li>{@code "Village"} → {@code ("en", "Village")}</li>
     * </ul>
     *
     * @param input        raw input string, may be {@code null}
     * @param fallbackLang language code to use when no valid prefix is found
     * @return a {@link LangTitle} record containing the chosen language and remaining title
     */
    default LangTitle splitLangPrefixedTitle(String input, String fallbackLang) {
        String s = input == null ? "" : input.trim();
        String lang = fallbackLang;
        int idx = s.indexOf(':');
        if (idx > 0 && idx <= 6) {
            String maybe = s.substring(0, idx);
            if (maybe.matches("[A-Za-z-]{2,6}")) {
                lang = maybe;
                s = s.substring(idx + 1).trim();
            }
        }
        return new LangTitle(lang, s);
    }

    /**
     * Returns the default language code used when input does not contain an explicit
     * language prefix.
     *
     * <p>Typical examples include {@code "en"}, {@code "zh-cn"} or {@code "ja"}.
     * Implementations can use any tag understood by the target site.</p>
     *
     * @return default language code (never {@code null})
     */
    String defaultLang();

    /**
     * Builds a full article URL from a language code and a page title.
     *
     * <p>This default implementation:
     * <ol>
     *     <li>Sanitizes the title via {@link #sanitizeTitle(String)} (spaces → underscores)</li>
     *     <li>URL-encodes the sanitized title using UTF-8</li>
     *     <li>Creates a URL in the form:
     *         {@code https://<lang>.<domainRoot()><pathPrefix()><encodedTitle>}</li>
     * </ol>
     *
     * <p>Implementations may override this method if a wiki has custom URL rules.</p>
     *
     * @param lang  language code to use in the subdomain (for example {@code "en"})
     * @param title raw article title as provided by the user
     * @return full URL string pointing to the article
     */
    default String titleToUrl(String lang, String title) {
        String sanitized = sanitizeTitle(title);
        String enc;
        try {
            enc = URLEncoder.encode(sanitized, StandardCharsets.UTF_8);
        } catch (Exception e) {
            enc = sanitized;
        }
        return "https://" + lang + "." + domainRoot() + pathPrefix() + enc;
    }

    /**
     * Performs basic sanitation on a wiki title.
     *
     * <p>The default implementation replaces spaces with underscores, mirroring
     * common wiki conventions. Implementations may override this if a wiki uses
     * a different normalization scheme.</p>
     *
     * @param title raw title, may be {@code null}
     * @return sanitized title, never {@code null}
     */
    default String sanitizeTitle(String title) {
        return title == null ? "" : title.replace(' ', '_');
    }

    /**
     * Returns the root domain for this wiki site (without language subdomain).
     *
     * <p>Examples:
     * <ul>
     *     <li>{@code "wikipedia.org"}</li>
     *     <li>{@code "minecraft.wiki"}</li>
     *     <li>{@code "example.com"}</li>
     * </ul>
     * The language portion is handled separately and prepended to this root domain.</p>
     *
     * @return root domain name (for example {@code "wikipedia.org"})
     */
    String domainRoot();

    /**
     * Returns the path prefix that appears before the encoded article title.
     *
     * <p>Different wiki engines use different paths. For example:
     * <ul>
     *     <li>Classic MediaWiki: {@code "/wiki/"}</li>
     *     <li>Minecraft Wiki: {@code "/w/"}</li>
     *     <li>Custom setups may even use {@code "/"} or other structures</li>
     * </ul>
     * This value is concatenated after the domain and before the encoded title.</p>
     *
     * @return path prefix used in article URLs (for example {@code "/wiki/"})
     */
    String pathPrefix();

    /**
     * Checks whether the given URL does <strong>not</strong> belong to this wiki site.
     *
     * <p>This is a convenience overload that uses {@link #domainRoot()} as the
     * expected domain. It returns {@code true} if the URL:
     * <ul>
     *     <li>cannot be parsed, or</li>
     *     <li>does not have a host ending with {@code "." + domainRoot()}, and</li>
     *     <li>is not exactly equal to {@code domainRoot()}</li>
     * </ul>
     *
     * @param url the URL to check
     * @return {@code true} if the URL is not part of this site, {@code false} otherwise
     */
    default boolean isNot(String url) {
        return isNot(url, domainRoot());
    }

    /**
     * Checks whether the given URL does <strong>not</strong> belong to a specific domain root.
     *
     * <p>The check is based on the host part of the URL. It returns {@code true} if:
     * <ul>
     *     <li>the URL is malformed or cannot be parsed, or</li>
     *     <li>the host does not equal {@code domain} and does not end with {@code "." + domain}</li>
     * </ul>
     * This method is intentionally conservative: invalid URLs are treated as "not belonging".</p>
     *
     * @param url    full URL string to test
     * @param domain expected root domain (for example {@code "wikipedia.org"})
     * @return {@code true} if the URL is considered outside the given domain, {@code false} otherwise
     */
    default boolean isNot(String url, String domain) {
        try {
            var u = new URI(url).toURL();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            return !host.endsWith("." + domain) && !host.equals(domain);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Parsed a pair of language and title derived from an input string.
     *
     * <p>Typical sources are strings like {@code "en:Article"} or {@code "Article"}
     * (with fallback language applied).</p>
     *
     * @param lang  resolved language code
     * @param title resolved article title
     */
    record LangTitle(String lang, String title) {
    }
}
