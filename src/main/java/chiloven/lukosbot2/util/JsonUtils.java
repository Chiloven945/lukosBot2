package chiloven.lukosbot2.util;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * JSON Utilities:<br>
 * - readJson / writeJson: File reading and writing (UTF-8, pretty print)<br>
 * - Supports Class and Type (generics)<br>
 * - Atomic write: First writes to a temporary file, then moves it to replace the original<br>
 * - loadOrCreate: If the file does not exist, creates and returns with a default value<br>
 * - fromJsonString / toJsonString: Convert between string and object<br>
 * - readResource: Reads from classpath resources (e.g., default configuration templates)<br>
 */
public final class JsonUtils {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonUtils() {
    }

    public static JsonUtils getJsonUtils() {
        return new JsonUtils();
    }

    /* ===================== File ←→ Object ===================== */

    /**
     * Reads JSON from a file and deserializes it into an object (supports Class)
     *
     * @param path file path
     * @param cls  target class
     * @param <T>  type parameter
     * @return deserialized object
     * @throws IOException         if file read fails
     * @throws JsonSyntaxException if JSON is malformed
     */
    public <T> T readFile(Path path, Class<T> cls) throws IOException, JsonSyntaxException {
        Objects.requireNonNull(path);
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, cls);
        }
    }

    /**
     * Reads JSON from a file and deserializes it into an object (supports Type for generics)
     *
     * @param path file path
     * @param type target type
     * @param <T>  type parameter
     * @return deserialized object
     * @throws IOException         if file read fails
     * @throws JsonSyntaxException if JSON is malformed
     */
    public <T> T readFile(Path path, Type type) throws IOException, JsonSyntaxException {
        Objects.requireNonNull(path);
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        }
    }

    /**
     * Serializes an object to JSON and writes it to a file (UTF-8, pretty print)
     * Uses atomic write: first writes to a temporary file, then moves it to replace the
     * original file
     *
     * @param path file path
     * @param obj  object to serialize
     * @throws IOException if file write fails
     */
    public void writeFile(Path path, Object obj) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(obj);

        Path dir = path.getParent();
        if (dir != null) Files.createDirectories(dir);

        String json = toJsonString(obj);

        String fn = path.getFileName().toString();
        String tmpName = fn + ".tmp-" + System.nanoTime();
        Path tmp = (path.getParent() == null) ? Path.of(tmpName) : path.getParent().resolve(tmpName);
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(json);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * If the file does not exist, creates it with default values and returns the defaults;
     * If it exists, reads and returns the deserialized object
     *
     * @param path     file path
     * @param cls      target class
     * @param defaults supplier for default object if file does not exist
     * @param <T>      type parameter
     * @return deserialized object or default object
     * @throws IOException if file read/write fails
     */
    public <T> T loadOrCreateFile(Path path, Class<T> cls, Supplier<T> defaults) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(cls);
        Objects.requireNonNull(defaults);

        if (Files.notExists(path)) {
            T def = defaults.get();
            writeFile(path, def);
            return def;
        }
        return readFile(path, cls);
    }

    /* ===================== String ←→ Object ===================== */

    /**
     * Deserializes a JSON string into an object (supports Class)
     *
     * @param json JSON string
     * @param cls  target class
     * @param <T>  type parameter
     * @return deserialized object
     * @throws JsonSyntaxException if JSON is malformed
     */
    public <T> T fromJsonString(String json, Class<T> cls) throws JsonSyntaxException {
        Objects.requireNonNull(json);
        return GSON.fromJson(json, cls);
    }

    /**
     * Deserializes a JSON string into an object (supports Type for generics)
     *
     * @param json JSON string
     * @param type target type
     * @param <T>  type parameter
     * @return deserialized object
     * @throws JsonSyntaxException if JSON is malformed
     */
    public <T> T fromJsonString(String json, Type type) throws JsonSyntaxException {
        Objects.requireNonNull(json);
        return GSON.fromJson(json, type);
    }

    /**
     * Serializes an object into a JSON string (pretty print)
     *
     * @param obj object to serialize
     * @return JSON string
     */
    public String toJsonString(Object obj) {
        Objects.requireNonNull(obj);
        return GSON.toJson(obj);
    }

    /* =====================  Resource Reading  ===================== */

    /**
     * Reads a JSON resource from the classpath and deserializes it into an object
     *
     * @param resourcePath path to the resource (e.g., "/default_config.json")
     * @param cls          target class
     * @param <T>          type parameter
     * @return deserialized object
     * @throws IOException         if resource not found or read fails
     * @throws JsonSyntaxException if JSON is malformed
     */
    public <T> T readResourceAs(String resourcePath, Class<T> cls) throws IOException {
        Objects.requireNonNull(resourcePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(JsonUtils.class.getResourceAsStream(resourcePath)), StandardCharsets.UTF_8))) {
            return GSON.fromJson(br, cls);
        } catch (NullPointerException npe) {
            throw new IOException("Unable to find the resource: " + resourcePath, npe);
        }
    }

    /* =====================  Private Helpers  ===================== */

    /**
     * Serializes an object into a JsonObject
     *
     * @param obj the object to serialize
     * @return the resulting JsonObject
     */
    public JsonObject toJsonObject(Object obj) {
        return GSON.toJsonTree(obj).getAsJsonObject();
    }

    /**
     * Deserializes a JsonObject into an object of the specified class
     *
     * @param obj the JsonObject to deserialize
     * @param cls the target class
     * @param <T> type parameter
     * @return the deserialized object
     */
    public <T> T fromJsonObject(JsonObject obj, Class<T> cls) {
        return GSON.fromJson(obj, cls);
    }

    /**
     * Deep merge src into dst (modifies dst).
     * If both dst and src have a value for the same key and both values are Json
     * objects, merge them recursively. Otherwise, overwrite dst's value with src's value.
     * This does not handle Json arrays specially; src's value
     * will overwrite dst's value if they are arrays.
     *
     * @param dst the destination JsonObject to merge into
     * @param src the source JsonObject to merge from
     */
    public void mergeInto(JsonObject dst, JsonObject src) {
        for (String key : src.keySet()) {
            JsonElement srcVal = src.get(key);
            // If dst does not have the key, add it
            if (!dst.has(key)) {
                dst.add(key, srcVal);
                continue;
            }

            JsonElement dstVal = dst.get(key);
            // If both values are JsonObjects, merge them recursively
            if (srcVal != null && srcVal.isJsonObject() && dstVal != null && dstVal.isJsonObject()) {
                mergeInto(dstVal.getAsJsonObject(), srcVal.getAsJsonObject());
            } else {
                dst.add(key, srcVal);
            }
        }
    }

    /**
     * Get a string value from a JsonObject by key, with a default if the key is missing or null.
     *
     * @param obj the JsonObject
     * @param key the key
     * @param def the default value
     * @return the string value or the default
     */
    public String getString(JsonObject obj, String key, String def) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : def;
    }

    /**
     * Get a long value from a JsonObject by key, with a default if the key is missing or null.
     *
     * @param obj the JsonObject
     * @param key the key
     * @param def the default value
     * @return the long value or the default
     */
    public long getLong(JsonObject obj, String key, long def) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : def;
    }

    /**
     * Get an int value from a JsonObject by key, with a default if the key is missing or null.
     *
     * @param obj the JsonObject
     * @param key the key
     * @param def the default value
     * @return the int value or the default
     */
    public int getInt(JsonObject obj, String key, int def) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : def;
    }

    /**
     * Get a JsonObject value from a JsonObject by key, or null if the key is missing or not a JsonObject.
     *
     * @param obj the JsonObject
     * @param key the key
     * @return the JsonObject value or null
     */
    public JsonObject getObj(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && obj.get(key).isJsonObject()) ? obj.getAsJsonObject(key) : null;
    }

    /**
     * Get a JsonArray from a JsonObject by key, or null if missing / not array.
     */
    public JsonArray getArray(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && obj.get(key).isJsonArray())
                ? obj.getAsJsonArray(key)
                : null;
    }

    /**
     * Get a JsonElement from a JsonObject by key, or null if missing.
     */
    public JsonElement getElement(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key)
                : null;
    }

    /**
     * Get a string from an array of objects:
     * e.g. key="properties", index=0, subKey="value"
     */
    public String getStringFromArrayObj(JsonObject obj, String arrayKey, int index, String subKey, String def) {
        if (obj == null || arrayKey == null || subKey == null) return def;

        JsonArray arr = getArray(obj, arrayKey);
        if (arr == null || index < 0 || index >= arr.size()) return def;

        JsonElement el = arr.get(index);
        if (el == null || !el.isJsonObject()) return def;

        return getString(el.getAsJsonObject(), subKey, def);
    }

    /**
     * Find first object in array where matchKey==matchValue, then return its targetKey as String.
     * e.g. arrayKey="properties", matchKey="name", matchValue="textures", targetKey="value"
     */
    public String findStringInArrayByKey(JsonObject obj,
                                         String arrayKey,
                                         String matchKey,
                                         String matchValue,
                                         String targetKey,
                                         String def) {
        if (obj == null) return def;

        JsonArray arr = getArray(obj, arrayKey);
        if (arr == null) return def;

        for (JsonElement e : arr) {
            if (e == null || !e.isJsonObject()) continue;
            JsonObject item = e.getAsJsonObject();

            String mk = getString(item, matchKey, null);
            if (mk != null && mk.equals(matchValue)) {
                return getString(item, targetKey, def);
            }
        }
        return def;
    }

    /**
     * Get JsonElement by simple path like: "properties[0].value" or "a.b.c"
     */
    public JsonElement getByPath(JsonObject obj, String path) {
        if (obj == null || path == null || path.isEmpty()) return null;

        JsonElement current = obj;
        String[] parts = path.split("\\.");

        for (String part : parts) {
            if (current == null || current.isJsonNull()) return null;

            // Handle something like "properties[0]"
            int lb = part.indexOf('[');
            if (lb >= 0) {
                String key = part.substring(0, lb);
                int rb = part.indexOf(']', lb);
                if (rb < 0) return null;

                String idxStr = part.substring(lb + 1, rb);
                int idx;
                try {
                    idx = Integer.parseInt(idxStr);
                } catch (NumberFormatException ex) {
                    return null;
                }

                if (!current.isJsonObject()) return null;
                JsonObject curObj = current.getAsJsonObject();
                JsonArray arr = getArray(curObj, key);
                if (arr == null || idx < 0 || idx >= arr.size()) return null;

                current = arr.get(idx);
            } else {
                if (!current.isJsonObject()) return null;
                current = current.getAsJsonObject().get(part);
            }
        }
        return (current == null || current.isJsonNull()) ? null : current;
    }

    /**
     * Get String by path with default.
     */
    public String getStringByPath(JsonObject obj, String path, String def) {
        JsonElement el = getByPath(obj, path);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : def;
    }
}
