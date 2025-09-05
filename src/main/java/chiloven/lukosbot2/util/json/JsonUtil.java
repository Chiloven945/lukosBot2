package chiloven.lukosbot2.util.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

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
public final class JsonUtil {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonUtil() {
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
    public static <T> T readJson(Path path, Class<T> cls) throws IOException, JsonSyntaxException {
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
    public static <T> T readJson(Path path, Type type) throws IOException, JsonSyntaxException {
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
    public static void writeJson(Path path, Object obj) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(obj);

        Path dir = path.getParent();
        if (dir != null) Files.createDirectories(dir);

        String json = toJsonString(obj);

        Path tmp = tempSibling(path);
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
    public static <T> T loadOrCreate(Path path, Class<T> cls, Supplier<T> defaults) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(cls);
        Objects.requireNonNull(defaults);

        if (Files.notExists(path)) {
            T def = defaults.get();
            writeJson(path, def);
            return def;
        }
        return readJson(path, cls);
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
    public static <T> T fromJsonString(String json, Class<T> cls) throws JsonSyntaxException {
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
    public static <T> T fromJsonString(String json, Type type) throws JsonSyntaxException {
        Objects.requireNonNull(json);
        return GSON.fromJson(json, type);
    }

    /**
     * Serializes an object into a JSON string (pretty print)
     *
     * @param obj object to serialize
     * @return JSON string
     */
    public static String toJsonString(Object obj) {
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
    public static <T> T readResource(String resourcePath, Class<T> cls) throws IOException {
        Objects.requireNonNull(resourcePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(JsonUtil.class.getResourceAsStream(resourcePath)), StandardCharsets.UTF_8))) {
            return GSON.fromJson(br, cls);
        } catch (NullPointerException npe) {
            throw new IOException("资源未找到: " + resourcePath, npe);
        }
    }

    /* =====================  Private Helpers  ===================== */

    /**
     * Generates a temporary sibling path for atomic file writing
     *
     * @param target the target file path
     * @return temporary sibling path
     */
    private static Path tempSibling(Path target) {
        String fn = target.getFileName().toString();
        String tmpName = fn + ".tmp-" + System.nanoTime();
        return (target.getParent() == null) ? Path.of(tmpName) : target.getParent().resolve(tmpName);
    }

    /**
     * Converts an object to its JSON string representation
     * (Pretty print, HTML characters not escaped)
     *
     * @param obj the object to convert
     * @return the JSON string representation
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * Converts an object to a JsonObject
     *
     * @param obj the object to convert
     * @return the JsonObject representation
     */
    public static JsonObject toJsonTree(Object obj) {
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
    public static <T> T fromJsonTree(JsonObject obj, Class<T> cls) {
        return GSON.fromJson(obj, cls);
    }

    /**
     * Normalizes a JsonObject to a pretty-printed JSON string
     *
     * @param obj the JsonObject to normalize
     * @return the pretty-printed JSON string
     */
    public static String normalizePretty(JsonObject obj) {
        return GSON.toJson(obj);
    }
}
