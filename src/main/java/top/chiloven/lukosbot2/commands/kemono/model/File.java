package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.util.TimeUtil;

import java.time.LocalDateTime;
import java.util.List;

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
                Post.fromJsonArray(obj.get("posts").getAsJsonArray())
        );
    }

    public @NonNull String getString() {
        StringBuilder sb = new StringBuilder();

        sb.append("ID: ").append(id).append("\n");
        sb.append("哈希值：").append(hash).append("\n");
        sb.append("添加时间：").append(added.format(TimeUtil.getDTF())).append("\n");
        sb.append("出现于：").append("\n");
        for (Post post : posts) {
            sb.append("  - ").append(post.getStringBrief()).append("\n");
        }

        return sb.toString();
    }
}
