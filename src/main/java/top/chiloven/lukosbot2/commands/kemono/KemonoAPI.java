package top.chiloven.lukosbot2.commands.kemono;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import top.chiloven.lukosbot2.commands.kemono.model.Service;
import top.chiloven.lukosbot2.util.HttpJson;
import top.chiloven.lukosbot2.util.SHAUtil;
import top.chiloven.lukosbot2.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Log4j2
public class KemonoAPI {
    private static final StringUtils SU = StringUtils.getStringUtils();
    private static final HttpJson HJ = HttpJson.getHttpJson();
    private static final SHAUtil SHAU = SHAUtil.getSHAUtil();

    private static final URI API = URI.create("https://kemono.cr/api/");
    private static final Map<String, String> HEADER = Map.of("Accept", "text/css");

    private KemonoAPI() {
    }

    public static KemonoAPI getKemonoAPI() {
        return new KemonoAPI();
    }

    private URI resolve(String path) {
        URI uri = API.resolve(path);
        log.debug("Kemono API request: {}", uri);
        return uri;
    }

    public JsonObject getSpecificPost(
            Service service,
            String creatorId,
            String postId
    ) throws IOException {
        return HJ.getObject(
                resolve("v1/%s/user/%s/post/%s".formatted(service, creatorId, postId)),
                HEADER
        );
    }

    public JsonObject getCreatorProfile(
            Service service,
            String creatorId
    ) throws IOException {
        return HJ.getObject(
                resolve("v1/%s/user/%s/profile".formatted(service, creatorId)),
                HEADER
        );
    }

    public JsonArray getCreatorLinks(
            Service service,
            String creatorId
    ) throws IOException {
        return HJ.getArray(
                resolve("v1/%s/user/%s/links".formatted(service, creatorId)),
                HEADER
        );
    }

    public JsonArray getCreatorPosts(
            Service service,
            String creatorId
    ) throws IOException {
        return HJ.getArray(
                resolve("v1/%s/user/%s/posts".formatted(service, creatorId)),
                HEADER
        );
    }

    public JsonArray getDiscordChannelPost(
            String channelId
    ) throws IOException {
        return HJ.getArray(
                resolve("v1/discord/channel/%s".formatted(channelId)),
                HEADER
        );
    }

    public JsonArray getDiscordServerChannel(
            String channelId
    ) throws IOException {
        return HJ.getArray(
                resolve("v1/discord/channel/lookup/%s".formatted(channelId)),
                HEADER
        );
    }

    public JsonObject getFileFromHash(
            String hash
    ) throws IOException {
        return HJ.getObject(
                resolve("v1/search_hash/%s".formatted(hash)),
                HEADER
        );
    }

    public JsonObject getPostFromServicePost(
            Service service,
            String servicePostId
    ) throws IOException {
        return HJ.getObject(
                resolve("v1/%s/post/%s".formatted(service, servicePostId)),
                HEADER
        );
    }
}
