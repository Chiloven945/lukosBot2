package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represent an embed stuff in the post.
 *
 * @param url         the url of the embed
 * @param subject     the title
 * @param description the description
 */
public record Embed(
        String url,
        String subject,
        String description
) {
    public static @Nullable Embed fromSpecificPost(JsonObject obj) {
        try {
            return new Embed(
                    obj.get("url").getAsString(),
                    obj.get("subject").getAsString(),
                    obj.get("description").getAsString()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public @NonNull String getString() {
        StringBuilder sb = new StringBuilder();

        sb.append("内嵌：").append(url).append("\n");
        if (!subject.equals("null")) {
            sb.append(subject).append("：\n");
        }
        if (!description.equals("null")) {
            sb.append(description).append("\n");
        }

        return sb.toString();
    }
}
