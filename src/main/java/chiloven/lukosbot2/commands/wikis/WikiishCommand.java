package chiloven.lukosbot2.commands.wikis;

import chiloven.lukosbot2.commands.BotCommand;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Common helpers for “wiki-like” commands:
 * <ul>
 *      <li>parse optional language prefix (e.g., "en:Title")</li>
 *      <li>normalize input (URL or title) to a full article URL</li>
 *      <li>verify URL belongs to the expected domain</li>
 *      <li>transform title to URL with proper encoding</li>
 * </ul>
 * <p>
 * Site-specific URL building stays abstract.
 *
 * @author Chiloven945
 */
public interface WikiishCommand extends BotCommand {

    // TODO: change to the language in the application.yml

    /**
     * Default language when input has no 'xx:' prefix (override if needed).
     */
    String defaultLang();

    /**
     * Path prefix before the encoded title; override for sites like Minecraft Wiki ("/w/").
     */
    String pathPrefix();

    /**
     * Site root domain, e.g. "wikipedia.org" or "minecraft.wiki".
     */
    String domainRoot();

    /**
     * Build full article URL; implementations may override if they need special rules.
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
     * Normalize user input to full site URL; accepts a URL or "en:Some Title".
     */
    default String normalize(String linkOrTitle) {
        String s = linkOrTitle == null ? "" : linkOrTitle.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        LangTitle lt = splitLangPrefixedTitle(s, defaultLang());
        return titleToUrl(lt.lang(), sanitizeTitle(lt.title()));
    }

    /**
     * Check whether the given URL does NOT belong to this site (based on {@link #domainRoot()}).
     */
    default boolean isNot(String url) {
        return isNot(url, domainRoot());
    }

    /**
     * Generic host check against a specific domain root (kept for flexibility).
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
     * Split optional "xx:Title" prefix; accepts 2–6 alpha/dash language tags, else use fallback.
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
     * Title sanitation common to both sites (keep spaces as underscores before encoding).
     */
    default String sanitizeTitle(String title) {
        return title == null ? "" : title.replace(' ', '_');
    }

    /**
     * Parsed (lang, title) pair from input like "en:Title" or "Title" (fallback lang applied).
     */
    record LangTitle(String lang, String title) {
    }
}
