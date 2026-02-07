package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Represent the attachment returned by the API.
 *
 * @param fileName the actual name of the file with a type suffix
 * @param path     the URI that the file is located at
 */
record Attachment(
        String fileName,
        URI path
) {
    private static final URI RES_PATH = URI.create("https://kemono.cr");

    public static List<Attachment> fromJsonArray(JsonArray arr) {
        List<Attachment> attachments = new ArrayList<>();

        for (JsonElement e : arr) {
            JsonObject obj = e.getAsJsonObject();
            attachments.add(new Attachment(
                    obj.get("name").getAsString(),
                    URI.create(obj.get("path").getAsString())
            ));
        }

        return attachments;
    }

    public @NonNull String getString() {
        return "%s：%s%s".formatted(fileName, RES_PATH, path);
    }
}
