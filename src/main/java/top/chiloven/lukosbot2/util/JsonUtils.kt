package top.chiloven.lukosbot2.util

import com.google.gson.*
import java.io.IOException
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * JSON Utilities:
 * - readFile / writeFile: File reading and writing (UTF-8, pretty print)
 * - Supports Class and Type (generics)
 * - Atomic write: First writes to a temporary file, then moves it to replace the original
 * - loadOrCreateFile: If the file does not exist, creates it with a default value and returns it
 * - fromJsonString / toJsonString: Convert between string and object
 * - readResourceAs: Reads from classpath resources (e.g., default configuration templates)
 *
 * Note:
 * This class is designed in an idiomatic Kotlin style:
 * - public APIs avoid nullable parameters
 * - defaults are provided via lambdas instead of Java Supplier
 */
object JsonUtils {
    val GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /* ===================== File <-> Object ===================== */

    @Throws(IOException::class, JsonSyntaxException::class)
    fun <T> readFile(path: Path, cls: Class<T>): T {
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { r ->
            return GSON.fromJson(r, cls)
        }
    }

    @Throws(IOException::class, JsonSyntaxException::class)
    fun <T> readFile(path: Path, type: Type): T {
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { r ->
            @Suppress("UNCHECKED_CAST")
            return GSON.fromJson<T>(r, type)
        }
    }

    /**
     * Serializes [obj] to JSON and writes it to [path] (UTF-8, pretty print).
     * Uses atomic write: first writes to a temporary file, then moves it to replace the original file.
     */
    @Throws(IOException::class)
    fun writeFile(path: Path, obj: Any) {
        val dir = path.parent
        if (dir != null) Files.createDirectories(dir)

        val json = toJsonString(obj)

        val fn = path.fileName.toString()
        val tmpName = "$fn.tmp-${System.nanoTime()}"
        val tmp = path.parent?.resolve(tmpName) ?: Path.of(tmpName)

        Files.newBufferedWriter(tmp, StandardCharsets.UTF_8).use { w ->
            w.write(json)
        }

        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * If [path] does not exist, creates it using [defaults] and returns that default object;
     * otherwise reads and returns the deserialized object.
     */
    @Throws(IOException::class)
    fun <T> loadOrCreateFile(path: Path, cls: Class<T>, defaults: () -> T): T {
        if (Files.notExists(path)) {
            val def = defaults()
            writeFile(path, def as Any)
            return def
        }
        return readFile(path, cls)
    }

    /* ===================== String <-> Object ===================== */

    @JvmStatic
    @Throws(JsonSyntaxException::class)
    fun <T> fromJsonString(json: String, cls: Class<T>): T {
        return GSON.fromJson(json, cls)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJsonString(json: String, type: Type): T {
        @Suppress("UNCHECKED_CAST")
        return GSON.fromJson<T>(json, type)
    }

    fun toJsonString(obj: Any): String = GSON.toJson(obj)

    /* ===================== Resource Reading ===================== */

    /**
     * Reads a JSON resource from the classpath and deserializes it into an object.
     *
     * @param resourcePath path to the resource (e.g., "/default_config.json")
     */
    @Throws(IOException::class, JsonSyntaxException::class)
    fun <T> readResourceAs(resourcePath: String, cls: Class<T>): T {
        val input = JsonUtils::class.java.getResourceAsStream(resourcePath)
            ?: throw IOException("Unable to find the resource: $resourcePath")

        input.reader(StandardCharsets.UTF_8).buffered().use { br ->
            return GSON.fromJson(br, cls)
        }
    }

    /* ===================== Helpers ===================== */

    fun toJsonObject(obj: Any): JsonObject = GSON.toJsonTree(obj).asJsonObject

    fun <T> fromJsonObject(obj: JsonObject, cls: Class<T>): T = GSON.fromJson(obj, cls)

    /**
     * Deep merge [src] into [dst] (modifies [dst]).
     * If both dst and src have a value for the same key and both values are JsonObjects, merge recursively.
     * Otherwise, overwrite dst's value with src's value.
     * Arrays are overwritten (no special handling).
     */
    fun mergeInto(dst: JsonObject, src: JsonObject) {
        for (key in src.keySet()) {
            val srcVal = src.get(key)
            if (!dst.has(key)) {
                dst.add(key, srcVal)
                continue
            }

            val dstVal = dst.get(key)
            if (srcVal.isJsonObject && dstVal.isJsonObject) {
                mergeInto(dstVal.asJsonObject, srcVal.asJsonObject)
            } else {
                dst.add(key, srcVal)
            }
        }
    }

    @JvmStatic
    fun getString(obj: JsonObject, key: String, def: String? = null): String? {
        val el = obj.get(key) ?: return def
        return if (!el.isJsonNull) el.asString else def
    }

    @JvmStatic
    fun getLong(obj: JsonObject, key: String, def: Long): Long {
        val el = obj.get(key) ?: return def
        return if (!el.isJsonNull) el.asLong else def
    }

    @JvmStatic
    fun getInt(obj: JsonObject, key: String, def: Int): Int {
        val el = obj.get(key) ?: return def
        return if (!el.isJsonNull) el.asInt else def
    }

    @JvmStatic
    fun getObj(obj: JsonObject, key: String): JsonObject? {
        val el = obj.get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    fun getArray(obj: JsonObject, key: String): JsonArray? {
        val el = obj.get(key) ?: return null
        return if (el.isJsonArray) el.asJsonArray else null
    }

    fun getElement(obj: JsonObject, key: String): JsonElement? {
        val el = obj.get(key) ?: return null
        return if (!el.isJsonNull) el else null
    }

    fun getStringFromArrayObj(
        obj: JsonObject,
        arrayKey: String,
        index: Int,
        subKey: String,
        def: String? = null
    ): String? {
        val arr = getArray(obj, arrayKey) ?: return def
        if (index !in 0 until arr.size()) return def

        val el = arr[index]
        if (!el.isJsonObject) return def

        return getString(el.asJsonObject, subKey, def)
    }

    fun findStringInArrayByKey(
        obj: JsonObject,
        arrayKey: String,
        matchKey: String,
        matchValue: String,
        targetKey: String,
        def: String? = null
    ): String? {
        val arr = getArray(obj, arrayKey) ?: return def

        for (e in arr) {
            if (!e.isJsonObject) continue
            val item = e.asJsonObject

            val mk = getString(item, matchKey, null)
            if (mk == matchValue) {
                return getString(item, targetKey, def)
            }
        }
        return def
    }

    fun getByPath(obj: JsonObject, path: String): JsonElement? {
        if (path.isEmpty()) return null

        var current: JsonElement = obj
        val parts = path.split('.')

        for (part in parts) {
            if (current.isJsonNull) return null

            val lb = part.indexOf('[')
            if (lb >= 0) {
                val key = part.substring(0, lb)
                val rb = part.indexOf(']', lb)
                if (rb < 0) return null

                val idx = part.substring(lb + 1, rb).toIntOrNull() ?: return null
                if (!current.isJsonObject) return null

                val curObj = current.asJsonObject
                val arr = getArray(curObj, key) ?: return null
                if (idx !in 0 until arr.size()) return null

                current = arr[idx]
            } else {
                if (!current.isJsonObject) return null
                val next = current.asJsonObject.get(part) ?: return null
                current = next
            }
        }

        return if (current.isJsonNull) null else current
    }

    @JvmStatic
    fun getStringByPath(obj: JsonObject, path: String, def: String? = null): String? {
        val el = getByPath(obj, path) ?: return def
        return if (el.isJsonPrimitive) el.asString else def
    }
}
