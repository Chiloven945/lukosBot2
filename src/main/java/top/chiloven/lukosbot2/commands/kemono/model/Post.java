package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import top.chiloven.lukosbot2.commands.kemono.KemonoAPI;
import top.chiloven.lukosbot2.util.TimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represent a post returned by the API.
 *
 * @param id          the id of the post
 * @param user        the creator id that post this post
 * @param service     where the post is posted on
 * @param title       human-readable title of the post
 * @param content     extra text information in the post
 * @param published   when did the post get published
 * @param attachments a list of attachments in the post
 */
public record Post(
        String id,
        String user,
        Service service,
        String title,
        @Nullable String content,
        @Nullable Embed embed,
        LocalDateTime published,
        @Nullable List<Attachment> attachments
) {
    /**
     * Return a {@link Post} instance for brief display ONLY.
     *
     * @param obj a single {@link JsonObject} fetched from {@link KemonoAPI#getCreatorPosts(Service, String)} in the array
     * @return a post instance
     * @see Post#fromBriefList(JsonArray)
     */
    public static Post fromBrief(JsonObject obj) {
        return new Post(
                obj.get("id").getAsString(),
                obj.get("user").getAsString(),
                Service.getService(obj.get("service").getAsString()),
                obj.get("title").getAsString(),
                null,
                null,
                LocalDateTime.parse(obj.get("published").getAsString()),
                null
        );
    }

    /**
     * Return a full {@link Post} instance that is suitable for all the cases like downloading and display full information.
     * This requires to fetch the post individually.
     *
     * @param obj a {@link JsonObject} fetched from {@link KemonoAPI#getSpecificPost(Service, String, String)}
     * @return a full post instance
     */
    public static Post fromSpecific(JsonObject obj) {
        obj = obj.getAsJsonObject("post");

        return new Post(
                obj.get("id").getAsString(),
                obj.get("user").getAsString(),
                Service.getService(obj.get("service").getAsString()),
                obj.get("title").getAsString(),
                obj.get("content").getAsString(),
                Embed.fromSpecificPost(obj.getAsJsonObject("embed")),
                LocalDateTime.parse(obj.get("published").getAsString()),
                Attachment.fromJsonArray(obj.get("file").getAsJsonObject(), obj.get("attachments").getAsJsonArray())
        );
    }

    /**
     * Resolve posts as {@link JsonObject} in a {@link JsonArray} return by {@link KemonoAPI#getCreatorPosts(Service, String)}.
     *
     * @param arr a JsonArray
     * @return a {@link List} of Posts
     * @see Post#fromBrief(JsonObject)
     */
    public static List<Post> fromBriefList(JsonArray arr) {
        List<Post> posts = new ArrayList<>();

        for (JsonElement e : arr) {
            posts.add(fromBrief(e.getAsJsonObject()));
        }

        return posts;
    }

    /**
     * Get a specific information text about the Post. Should NOT be called if the Post is created by {@link Post#fromBrief(JsonObject)}.
     *
     * @return specific descriptive information
     */
    public @NonNull String getSpecific() {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append("\n");
        sb.append("（").append(id).append("/").append(user).append("，").append(service.getName()).append("）").append("\n");
        sb.append("发布于：").append(published.format(TimeUtils.getDTF()));

        if (content != null && !content.isEmpty()) {
            sb.append("\n\n").append(content);
        }

        if (embed != null && !embed.getString().isEmpty()) {
            sb.append("\n\n").append(embed.getString());
        }

        if (attachments != null) {
            sb.append("\n\n附件：").append("\n");
            for (Attachment a : attachments) {
                sb.append(a.getString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Get a brief information text about the Post. Better for a list view.
     *
     * @return brief information text
     */
    public @NonNull String getBrief() {
        return "%s：https://kemono.cr/%s/user/%s/post/%s".formatted(title, service, user, id);
    }
}
