package top.chiloven.lukosbot2.commands.kemono.model;

import lombok.Getter;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The service that kemono.cr supports
 */
@Getter
public enum Service {
    PATREON("patreon", "Patreon"),
    FANBOX("fanbox", "Pixiv Fanbox"),
    //DISCORD("discord", "Discord"), (currently not supported)
    FANTIA("fantia", "Fantia"),
    AFDIAN("afdian", "Afdian"),
    BOOSTY("boosty", "Boosty"),
    GUMROAD("gumroad", "Gumroad"),
    SUBSCRIBE_STAR("subscribestar", "SubscribeStar"),
    DL_SITE("dlsite", "DLSite"),
    UNKNOWN("unknown", "Unknown");

    private static final Map<String, Service> SERVICE_MAP = new HashMap<>();

    static {
        for (Service s : values()) {
            SERVICE_MAP.put(s.id, s);
        }
    }

    private final String id;
    private final String name;

    Service(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Service getService(String id) {
        return SERVICE_MAP.getOrDefault(id, UNKNOWN);
    }

    /**
     * Parse the post information and the specific service in the service URL.
     *
     * @param uri a URI stored the URL
     * @return a {@link ServiceAndPostId} instance stored with a {@link Service} and a {@link String} service post id
     * @see ServiceAndPostId
     */
    public static ServiceAndPostId parseServicePostUrl(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

        try {
            if (host.contains("patreon.com")) {
                String id = Pattern.compile("-(\\d+)(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(PATREON, id);
            }
            if (host.contains("fanbox.cc")) {
                String id = Pattern.compile("/posts/(\\d+)(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(FANBOX, id);
            }
            if (host.contains("fantia.jp")) {
                String id = Pattern.compile("/posts/(\\d+)(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(FANTIA, id);
            }
            if (host.contains("afdian.net")) {
                String id = Pattern.compile("/p/([a-f0-9]+)(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(AFDIAN, id);
            }
            if (host.contains("boosty.to")) {
                String id = Pattern.compile("/posts/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(BOOSTY, id);
            }
            if (host.contains("gumroad.com")) {
                throw new IllegalArgumentException("无法对 Gumroad 的链接进行解析……");
            }
            if (host.contains("subscribestar.com")) {
                String id = Pattern.compile("/posts/(\\d+)(?:[/?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(SUBSCRIBE_STAR, id);
            }
            if (host.contains("dlsite.com")) {
                String id = Pattern.compile("/product_id/([A-Z0-9]+)\\.html(?:[?#].*)?$").matcher(uri.toString()).group(1);
                return new ServiceAndPostId(DL_SITE, id);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("错误的平台链接：" + uri);
        }

        throw new IllegalArgumentException("不支持的平台链接: " + uri);
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * A record with a {@link Service} and a {@link String} service post id.
     *
     * @param service       the service of the post
     * @param servicePostId the service post id
     */
    public record ServiceAndPostId(Service service, String servicePostId) {
    }
}
