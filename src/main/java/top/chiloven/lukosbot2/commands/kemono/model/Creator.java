package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.util.TimeUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represent the creator information returned by API.
 *
 * @param id      creator id
 * @param name    human-readable name
 * @param service where the creator is located on
 * @param indexed when did this creator get indexed on kemono.cr
 * @param updated when did this creator get updated
 * @param posts   the total post count
 */
public record Creator(
        String id,
        String name,
        Service service,
        LocalDateTime indexed,
        LocalDateTime updated,
        List<Post> posts
) {
    public static Creator fromProfileAndPosts(JsonObject profile, JsonArray posts) {
        return new Creator(
                profile.get("id").getAsString(),
                profile.get("name").getAsString(),
                Service.getService(profile.get("service").getAsString()),
                LocalDateTime.parse(profile.get("indexed").getAsString()),
                LocalDateTime.parse(profile.get("updated").getAsString()),
                Post.fromJsonArray(posts)
        );
    }

    public @NonNull String getString() {
        StringBuilder sb = new StringBuilder();

        sb.append(name).append("（").append(id).append("，").append(service.getName()).append("）").append("\n");
        sb.append("收录于：").append(indexed.format(TimeUtil.getDTF())).append("\n");
        sb.append("更新于：").append(updated.format(TimeUtil.getDTF())).append("\n\n");

        sb.append("帖子：").append("\n");
        for (int i = 0; i < Math.min(10, posts.size()); i++) {
            sb.append(posts.get(i).getStringBrief()).append("\n");
        }
        if (posts.size() > 10) {
            sb.append("……共 ").append(posts.size()).append(" 条，仅显示前 10 条").append("\n");
        }

        return sb.toString();
    }
}
