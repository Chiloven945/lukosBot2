package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.util.TimeUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represent a file information return by the {@code hash_search}
 *
 * @param id    the id of the file
 * @param hash  the hash
 * @param added the time that the file get added
 * @param posts the Post(s) that the file appear on
 */
public record File(
        String id,
        String hash,
        LocalDateTime added,
        List<Post> posts
) {
    public static File fromJsonObject(JsonObject obj) {
        return new File(
                obj.get("id").getAsString(),
                obj.get("hash").getAsString(),
                LocalDateTime.parse(obj.get("added").getAsString()),
                Post.fromBriefList(obj.get("posts").getAsJsonArray())
        );
    }

    public @NonNull String getString() {
        StringBuilder sb = new StringBuilder();

        sb.append("ID: ").append(id).append("\n");
        sb.append("哈希值：").append(hash).append("\n");
        sb.append("添加时间：").append(added.format(TimeUtils.getDTF())).append("\n");
        sb.append("出现于：").append("\n");
        for (Post post : posts) {
            sb.append("  - ").append(post.getBrief()).append("\n");
        }

        return sb.toString();
    }
}
