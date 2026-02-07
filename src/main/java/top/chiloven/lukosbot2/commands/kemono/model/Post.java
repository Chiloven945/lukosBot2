package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.util.TimeUtil;

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
 * @param subString   extra text information in the post
 * @param published   when did the post get published
 * @param attachments a list of attachments in the post
 */
public record Post(
        String id,
        String user,
        Service service,
        String title,
        String subString,
        LocalDateTime published,
        List<Attachment> attachments
) {
    public static Post fromJsonObject(JsonObject jsonObject) {
        JsonObject obj = jsonObject;

        if (obj.has("post") && obj.get("post").isJsonObject()) {
            obj = obj.getAsJsonObject("post");
        }

        return new Post(
                obj.get("id").getAsString(),
                obj.get("user").getAsString(),
                Service.getService(obj.get("service").getAsString()),
                obj.get("title").getAsString(),
                obj.has("substring") ? obj.get("substring").getAsString() : (obj.has("content") ? obj.get("content").getAsString() : "no substring"),
                java.time.LocalDateTime.parse(obj.get("published").getAsString()),
                Attachment.fromJsonArray(obj.get("attachments").getAsJsonArray())
        );
    }

    public static List<Post> fromJsonArray(JsonArray arr) {
        List<Post> posts = new ArrayList<>();

        for (JsonElement e : arr) {
            posts.add(fromJsonObject(e.getAsJsonObject()));
        }

        return posts;
    }

    public @NonNull String getString() {
        StringBuilder sb = new StringBuilder();

        sb.append(title).append("\n");
        sb.append("（").append(id).append("/").append(user).append("，").append(service.getName()).append("）").append("\n");
        sb.append("发布于：").append(published.format(TimeUtil.getDTF())).append("\n\n");

        sb.append(subString).append("\n\n");

        sb.append("附件：").append("\n");
        for (Attachment a : attachments) {
            sb.append(a.getString()).append("\n");
        }

        return sb.toString();
    }

    public @NonNull String getStringBrief() {
        return "%s：https://kemono.cr/%s/user/%s/post/%s".formatted(title, service, user, id);
    }
}
