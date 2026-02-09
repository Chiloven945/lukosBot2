package top.chiloven.lukosbot2.commands.kemono.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represent the attachment returned by the API.
 *
 * @param fileName the actual name of the file with a type suffix
 * @param path     the URI that the file is located at
 */
public record Attachment(
        String fileName,
        String path
) {
    private static final URI RES_PATH = URI.create("https://kemono.cr");

    public static @Nullable List<Attachment> fromJsonArray(JsonObject file, JsonArray att) {
        try {
            Set<String> seen = new HashSet<>();

            return Stream.concat(
                            Stream.of(file),
                            StreamSupport.stream(
                                    Optional.ofNullable(att)
                                            .orElse(new JsonArray())
                                            .spliterator(),
                                    false)
                    ).filter(e -> e != null && e.isJsonObject())
                    .map(e -> fromJsonObject(e.getAsJsonObject()))
                    .filter(Objects::nonNull)
                    .filter(a -> {
                        String key = dedupKey(a);
                        return key != null && seen.add(key);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }

    public static @Nullable Attachment fromJsonObject(JsonObject obj) {
        if (obj == null) return null;

        try {
            String name = Optional.ofNullable(obj.get("fileName"))
                    .or(() -> Optional.ofNullable(obj.get("name")))
                    .filter(e -> !e.isJsonNull())
                    .map(JsonElement::getAsString)
                    .orElse(null);

            String path = Optional.ofNullable(obj.get("path"))
                    .filter(e -> !e.isJsonNull())
                    .map(JsonElement::getAsString)
                    .orElse(null);

            return (name != null && path != null) ? new Attachment(name, path) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String dedupKey(Attachment a) {
        if (a == null) return null;
        String p = a.path();
        if (p != null && !p.isBlank()) return "p:" + p.trim();
        String n = a.fileName();
        if (n != null && !n.isBlank()) return "n:" + n.trim();
        return null;
    }

    public @NonNull String getString() {
        return "%s：%s%s".formatted(fileName, RES_PATH, path);
    }
}
