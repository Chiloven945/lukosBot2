package top.chiloven.lukosbot2.commands.kemono.model;

import lombok.Getter;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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

    // TODO: service url parsing
    public static ServiceAndPostId parseServicePostUrl(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

//        if (host.contains("patreon.com")) {
//            return new ServiceAndPostId(PATREON, parsePatreonPostId(uri));
//        }
//        if (host.contains("fanbox.cc")) {
//            return new ServiceAndPostId(FANBOX, parseFanboxPostId(uri));
//        }
//        if (host.contains("fantia.jp")) {
//            return new ServiceAndPostId(FANTIA, parseFantiaPostId(uri));
//        }
//        if (host.contains("afdian.net")) {
//            return new ServiceAndPostId(AFDIAN, parseAfdianPostId(uri));
//        }
//        if (host.contains("boosty.to")) {
//            return new ServiceAndPostId(BOOSTY, parseBoostyPostId(uri));
//        }
//        if (host.contains("gumroad.com")) {
//            return new ServiceAndPostId(GUMROAD, parseGumroadPostId(uri));
//        }
//        if (host.contains("subscribestar.com")) {
//            return new ServiceAndPostId(SUBSCRIBE_STAR, parseSubscribeStarPostId(uri));
//        }
//        if (host.contains("dlsite.com")) {
//            return new ServiceAndPostId(DL_SITE, parseDlsitePostId(uri));
//        }

        throw new IllegalArgumentException("不支持的平台链接: " + uri);
    }

    @Override
    public String toString() {
        return id;
    }

    public record ServiceAndPostId(Service service, String servicePostId) {
    }

}
