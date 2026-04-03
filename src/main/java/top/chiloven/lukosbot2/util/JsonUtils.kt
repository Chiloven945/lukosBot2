package top.chiloven.lukosbot2.util

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.util.StdConverter
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import top.chiloven.lukosbot2.util.JsonUtils.MAPPER
import top.chiloven.lukosbot2.util.JsonUtils.SNAKE_CASE_MAPPER
import top.chiloven.lukosbot2.util.JsonUtils.getArray
import top.chiloven.lukosbot2.util.JsonUtils.getBoolean
import top.chiloven.lukosbot2.util.JsonUtils.getByPath
import top.chiloven.lukosbot2.util.JsonUtils.getElement
import top.chiloven.lukosbot2.util.JsonUtils.getFloat
import top.chiloven.lukosbot2.util.JsonUtils.getInt
import top.chiloven.lukosbot2.util.JsonUtils.getLong
import top.chiloven.lukosbot2.util.JsonUtils.getObj
import top.chiloven.lukosbot2.util.JsonUtils.getString
import top.chiloven.lukosbot2.util.JsonUtils.snakeTreeToValue
import top.chiloven.lukosbot2.util.JsonUtils.writeFile
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Central JSON utility for the project.
 *
 * This object provides a single place for the three JSON-related concerns that occur repeatedly
 * across the codebase:
 *
 * 1. **Object binding**
 *    - Convert JSON strings into Kotlin / Java objects.
 *    - Convert objects back into JSON strings.
 *    - Convert between Jackson tree nodes and typed objects.
 *
 * 2. **File persistence**
 *    - Read JSON files using UTF-8.
 *    - Write JSON files using the project's shared mapper configuration.
 *    - Initialize missing files with default values.
 *
 * 3. **Tree-model convenience access**
 *    - Read scalar values, child objects, child arrays, and arbitrary nested paths from
 *      [ObjectNode] instances.
 *    - Provide a compact, null-tolerant access style for external API payloads and ad-hoc JSON.
 *
 * ## Mapper strategy
 *
 * The utility exposes two reusable [JsonMapper] instances:
 *
 * - [MAPPER] for the normal project-wide camelCase mapping strategy.
 * - [SNAKE_CASE_MAPPER] for APIs that expose `snake_case` field names while the local model keeps
 *   camelCase property names.
 *
 * ## Error handling philosophy
 *
 * The API intentionally follows two different styles depending on the use case:
 *
 * - **Binding / I/O methods** throw [IOException] and/or [JacksonException] when something is
 *   actually wrong.
 * - **Tree helper methods** are intentionally permissive and usually return `null` or a caller-
 *   supplied default value when a field is missing, `null`, or structurally incompatible.
 *
 * This split keeps strict failures for persistence and schema binding, while still making external
 * JSON payload handling concise in command implementations.
 *
 * ## Thread safety
 *
 * [JsonMapper] instances are configured once and then reused. They are safe to share across the
 * application.
 *
 * @author Chiloven945
 */
object JsonUtils {

    /**
     * Shared mapper used for the project's default JSON behavior.
     *
     * Configuration highlights:
     * - Registers the Kotlin module so Kotlin data classes, nullable properties, and default
     *   constructor values behave correctly.
     * - Enables pretty printing to keep persisted JSON readable during manual inspection.
     * - Disables timestamp-based date output so Java time values are written as textual ISO-like
     *   values instead of numeric epoch timestamps.
     *
     * This mapper should be used for:
     * - Local configuration/state files.
     * - General object <-> JSON conversion.
     * - Tree-model conversion where field names already match the local model.
     */
    @JvmField
    val MAPPER: JsonMapper = jsonMapper {
        addModule(kotlinModule())
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    /**
     * Variant of [MAPPER] that applies the [PropertyNamingStrategies.SNAKE_CASE] naming strategy.
     *
     * This mapper is intended for external services that return names like `created_at`,
     * `file_url`, or `preview_width` while the local class uses standard camelCase properties such
     * as `createdAt`, `fileUrl`, or `previewWidth`.
     *
     * Only the naming strategy differs from [MAPPER]; all other mapper configuration is preserved.
     */
    @JvmField
    val SNAKE_CASE_MAPPER: JsonMapper = MAPPER.rebuild()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()

    /**
     * Convert an [ObjectNode] into a typed object using [SNAKE_CASE_MAPPER].
     *
     * This is a convenience wrapper around Jackson tree binding for APIs whose payload uses
     * `snake_case` naming.
     *
     * @param node source object node
     * @param clazz target class to bind into
     * @return the bound object
     * @throws JacksonException if the node cannot be bound to [clazz]
     */
    fun <T> snakeTreeToValue(node: ObjectNode, clazz: Class<T>): T =
        SNAKE_CASE_MAPPER.treeToValue(node, clazz)

    /**
     * Convert an [ArrayNode] into a `List<T>` using [SNAKE_CASE_MAPPER].
     *
     * This is the collection counterpart of [snakeTreeToValue] and is typically used when an HTTP
     * endpoint returns a JSON array whose element objects follow `snake_case` naming.
     *
     * @param node source array node
     * @param clazz element type of the resulting list
     * @return a list containing the bound elements
     * @throws JacksonException if any element cannot be bound to [clazz]
     */
    fun <T> snakeTreeToList(node: ArrayNode, clazz: Class<T>): List<T> =
        SNAKE_CASE_MAPPER.readValue(
            SNAKE_CASE_MAPPER.treeAsTokens(node),
            SNAKE_CASE_MAPPER.typeFactory.constructCollectionType(List::class.java, clazz)
        )

    /**
     * Converter used by [JsonLdt] to deserialize textual date-time values into [LocalDateTime].
     *
     * Supported inputs are intentionally broader than Jackson's default `LocalDateTime`
     * deserializer. The converter currently accepts:
     *
     * - Offset-aware text such as `2026-04-03T07:06:35.098-04:00`
     * - Plain local date-time text such as `2026-04-03T07:06:35`
     *
     * Conversion order:
     * 1. Attempt [OffsetDateTime.parse] and convert to local date-time via [toLDT].
     * 2. Fallback to [LocalDateTime.parse].
     *
     * Blank input is treated as absent and returns `null`.
     */
    class LdtConverter : StdConverter<String, LocalDateTime>() {

        /**
         * Convert a textual JSON value into a [LocalDateTime].
         *
         * @param value raw textual value from JSON; may be `null`
         * @return parsed [LocalDateTime], or `null` if the input is blank
         * @throws IllegalArgumentException if [value] is present but does not match any supported
         * format
         */
        override fun convert(value: String?): LocalDateTime? {
            if (value.isNullOrBlank()) return null

            return runCatching { OffsetDateTime.parse(value).toLDT() }
                .recoverCatching { LocalDateTime.parse(value) }
                .getOrElse {
                    throw IllegalArgumentException("Unsupported date-time value: $value", it)
                }
        }

    }

    /**
     * Composite annotation for fields or constructor parameters that should be deserialized using
     * [LdtConverter].
     *
     * This is primarily useful when third-party JSON payloads provide date-time strings in a format
     * that may include an offset, while the local model still wants to store the value as
     * [LocalDateTime].
     *
     * Example:
     * ```kotlin
     * data class Post(
     *     @JsonLdt
     *     val createdAt: LocalDateTime?
     * )
     * ```
     */
    @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    @JsonDeserialize(converter = LdtConverter::class)
    @JacksonAnnotationsInside
    annotation class JsonLdt

    /**
     * Read a JSON file and bind it to a concrete class.
     *
     * The file is read as UTF-8 and deserialized with [MAPPER].
     *
     * @param path path to the JSON file
     * @param cls target class
     * @return the deserialized object
     * @throws IOException if the file cannot be opened or read
     * @throws JacksonException if the JSON content is invalid or incompatible with [cls]
     */
    @Throws(IOException::class, JacksonException::class)
    fun <T> readFile(path: Path, cls: Class<T>): T {
        Files.newBufferedReader(path, Charsets.UTF_8).use { r ->
            return MAPPER.readValue(r, cls)
        }
    }

    /**
     * Read a JSON file and bind it using a generic [Type].
     *
     * This overload is intended for cases where [Class] is insufficient, such as `List<Foo>`,
     * `Map<String, Bar>`, or other parameterized types.
     *
     * @param path path to the JSON file
     * @param type target generic type
     * @return the deserialized object
     * @throws IOException if the file cannot be opened or read
     * @throws JacksonException if the JSON content is invalid or incompatible with [type]
     */
    @Throws(IOException::class, JacksonException::class)
    fun <T> readFile(path: Path, type: Type): T {
        Files.newBufferedReader(path, Charsets.UTF_8).use { r ->
            return MAPPER.readValue(r, MAPPER.typeFactory.constructType(type))
        }
    }

    /**
     * Serialize [obj] and write it to [path].
     *
     * The method tries to keep file updates safe by writing to a temporary file first and then
     * replacing the original target file. If the underlying filesystem does not support atomic
     * move operations, it transparently falls back to a normal replacement move.
     *
     * Parent directories are created automatically when necessary.
     *
     * @param path target file path
     * @param obj object to serialize
     * @throws IOException if directory creation, file writing, or file replacement fails
     */
    @Throws(IOException::class)
    fun writeFile(path: Path, obj: Any) {
        val dir = path.parent
        if (dir != null) Files.createDirectories(dir)

        val json = toJsonString(obj)
        val fn = path.fileName.toString()
        val tmpName = "$fn.tmp-${System.nanoTime()}"
        val tmp = path.parent?.resolve(tmpName) ?: Path.of(tmpName)

        Files.newBufferedWriter(tmp, Charsets.UTF_8).use { w ->
            w.write(json)
        }

        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Load a JSON file if it exists; otherwise create it from a default value.
     *
     * This method is useful for bootstrap-style configuration and state files that should be
     * auto-created on first run.
     *
     * Behavior:
     * - If the file already exists, it is read and returned.
     * - If the file does not exist, [defaults] is invoked, the result is written to disk via
     *   [writeFile], and the same default instance is returned.
     *
     * @param path target file path
     * @param cls target class for reading
     * @param defaults supplier used when the file does not yet exist
     * @return the loaded or newly created value
     * @throws IOException if file creation, reading, or writing fails
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

    /**
     * Deserialize a JSON string into a concrete class using [MAPPER].
     *
     * @param json source JSON text
     * @param cls target class
     * @return deserialized object
     * @throws JacksonException if [json] is invalid or incompatible with [cls]
     */
    @JvmStatic
    @Throws(JacksonException::class)
    fun <T> fromJsonString(json: String, cls: Class<T>): T = MAPPER.readValue(json, cls)

    /**
     * Deserialize a JSON string into a generic [Type] using [MAPPER].
     *
     * @param json source JSON text
     * @param type target type, including generic information when needed
     * @return deserialized object
     * @throws JacksonException if [json] is invalid or incompatible with [type]
     */
    @Throws(JacksonException::class)
    fun <T> fromJsonString(json: String, type: Type): T =
        MAPPER.readValue(json, MAPPER.typeFactory.constructType(type))

    /**
     * Serialize an object into a JSON string using [MAPPER].
     *
     * Because pretty printing is enabled on [MAPPER], the output is intentionally human-readable
     * rather than compact.
     *
     * @param obj object to serialize
     * @return JSON representation of [obj]
     * @throws JacksonException if [obj] cannot be serialized
     */
    fun toJsonString(obj: Any): String = MAPPER.writeValueAsString(obj)

    /**
     * Read a JSON resource from the application's classpath and bind it to a class.
     *
     * Typical use cases include default templates, embedded schemas, and built-in configuration
     * resources shipped inside `src/main/resources`.
     *
     * @param resourcePath classpath resource path, e.g. `"/defaults/foo.json"`
     * @param cls target class
     * @return deserialized object
     * @throws IOException if the resource cannot be found or read
     * @throws JacksonException if the resource content is invalid or incompatible with [cls]
     */
    @Throws(IOException::class, JacksonException::class)
    fun <T> readResourceAs(resourcePath: String, cls: Class<T>): T {
        val input = JsonUtils::class.java.getResourceAsStream(resourcePath)
            ?: throw IOException("Unable to find the resource: $resourcePath")

        input.reader(Charsets.UTF_8).buffered().use { br ->
            return MAPPER.readValue(br, cls)
        }
    }

    /**
     * Convert an arbitrary object into an [ObjectNode].
     *
     * This is convenient when a typed object needs to be patched, merged, or inspected using the
     * Jackson tree model.
     *
     * @param obj object to convert
     * @return the resulting object node
     */
    fun toObjectNode(obj: Any): ObjectNode = MAPPER.valueToTree(obj)

    /**
     * Bind an [ObjectNode] to a typed object using the default [MAPPER].
     *
     * Unlike [snakeTreeToValue], this method uses the standard project naming strategy and should
     * be used when JSON field names already match the local model.
     *
     * @param node source node
     * @param cls target class
     * @return deserialized object
     * @throws JacksonException if [node] cannot be bound to [cls]
     */
    fun <T> fromJsonObject(node: ObjectNode, cls: Class<T>): T = MAPPER.treeToValue(node, cls)

    /**
     * Merge the contents of [src] into [dst] in place.
     *
     * Merge rules:
     * - If a property exists only in [src], it is copied into [dst].
     * - If both values are objects, they are merged recursively.
     * - In all other cases, the value from [src] overwrites the value in [dst].
     *
     * Arrays are not merged element-by-element; they are replaced as a whole.
     *
     * @param dst destination node to mutate
     * @param src source node whose values should be applied
     */
    fun mergeInto(dst: ObjectNode, src: ObjectNode) {
        src.properties().forEach { entry ->
            val key = entry.key
            val srcVal = entry.value

            if (!dst.has(key)) {
                dst.set(key, srcVal)
                return@forEach
            }

            val dstVal = dst.get(key)
            if (srcVal is ObjectNode && dstVal is ObjectNode) {
                mergeInto(dstVal, srcVal)
            } else {
                dst.set(key, srcVal)
            }
        }
    }

    /**
     * Read a string-like value from an object node.
     *
     * Behavior:
     * - Returns [def] if the field is absent.
     * - Returns [def] if the field is explicit JSON `null`.
     * - Otherwise returns [JsonNode.asString] on the underlying value node.
     *
     * @param obj source object
     * @param key field name
     * @param def default value returned when the field is missing or null
     * @return extracted string or [def]
     */
    @JvmStatic
    fun getString(obj: ObjectNode, key: String, def: String? = null): String? {
        val node = obj.get(key) ?: return def
        return if (!node.isNull) node.asString() else def
    }

    /**
     * Kotlin extension alias of [getString].
     *
     * @param key field name
     * @param def default value returned when the field is missing or null
     * @return extracted string or [def]
     */
    fun ObjectNode.str(key: String, def: String? = null): String? = getString(this, key, def)

    /**
     * Read a `Long`-like value from an object node.
     *
     * Missing or JSON-null fields return [def]. Otherwise [JsonNode.asLong] is used.
     *
     * @param obj source object
     * @param key field name
     * @param def default value
     * @return extracted long or [def]
     */
    @JvmStatic
    fun getLong(obj: ObjectNode, key: String, def: Long? = null): Long? {
        val node = obj.get(key) ?: return def
        return if (!node.isNull) node.asLong() else def
    }

    /**
     * Kotlin extension alias of [getLong].
     */
    fun ObjectNode.long(key: String, def: Long? = null): Long? = getLong(this, key, def)

    /**
     * Read an `Int`-like value from an object node.
     *
     * Missing or JSON-null fields return [def]. Otherwise [JsonNode.asInt] is used.
     *
     * @param obj source object
     * @param key field name
     * @param def default value
     * @return extracted int or [def]
     */
    @JvmStatic
    fun getInt(obj: ObjectNode, key: String, def: Int? = null): Int? {
        val node = obj.get(key) ?: return def
        return if (!node.isNull) node.asInt() else def
    }

    /**
     * Kotlin extension alias of [getInt].
     */
    fun ObjectNode.int(key: String, def: Int? = null): Int? = getInt(this, key, def)

    /**
     * Read a `Float`-like value from an object node.
     *
     * The implementation reads the value through [JsonNode.asDouble] and then narrows it to
     * `Float`. Missing or JSON-null fields return [def].
     *
     * @param obj source object
     * @param key field name
     * @param def default value
     * @return extracted float or [def]
     */
    @JvmStatic
    fun getFloat(obj: ObjectNode, key: String, def: Float? = null): Float? {
        val node = obj.get(key) ?: return def
        return if (!node.isNull) node.asDouble().toFloat() else def
    }

    /**
     * Kotlin extension alias of [getFloat].
     */
    fun ObjectNode.float(key: String, def: Float? = null): Float? = getFloat(this, key, def)

    /**
     * Read a boolean-like value from an object node.
     *
     * Missing or JSON-null fields return [def]. Otherwise [JsonNode.asBoolean] is used.
     *
     * @param obj source object
     * @param key field name
     * @param def default value
     * @return extracted boolean or [def]
     */
    @JvmStatic
    fun getBoolean(obj: ObjectNode, key: String, def: Boolean? = null): Boolean? {
        val node = obj.get(key) ?: return def
        return if (!node.isNull) node.asBoolean() else def
    }

    /**
     * Kotlin extension alias of [getBoolean].
     */
    fun ObjectNode.bool(key: String, def: Boolean? = null): Boolean? = getBoolean(this, key, def)

    /**
     * Read a child object node from an [ObjectNode].
     *
     * The method returns `null` when the field is absent, explicitly null, or not an object.
     *
     * @param obj source object
     * @param key field name
     * @return child object node, or `null`
     */
    @JvmStatic
    fun getObj(obj: ObjectNode, key: String): ObjectNode? = obj.get(key)?.asObjectOpt()?.orElse(null)

    /**
     * Kotlin extension alias of [getObj].
     */
    fun ObjectNode.obj(key: String): ObjectNode? = getObj(this, key)

    /**
     * Read a child array node from an [ObjectNode].
     *
     * The method returns `null` when the field is absent, explicitly null, or not an array.
     *
     * @param obj source object
     * @param key field name
     * @return child array node, or `null`
     */
    fun getArray(obj: ObjectNode, key: String): ArrayNode? = obj.get(key)?.asArrayOpt()?.orElse(null)

    /**
     * Kotlin extension alias of [getArray].
     */
    fun ObjectNode.arr(key: String): ArrayNode? = getArray(this, key)

    /**
     * Read the raw non-null child node from an object.
     *
     * Unlike the typed helpers, this method does not attempt scalar conversion. It simply returns
     * the underlying [JsonNode] when the field exists and is not JSON `null`.
     *
     * @param obj source object
     * @param key field name
     * @return raw node, or `null`
     */
    fun getElement(obj: ObjectNode, key: String): JsonNode? {
        val node = obj.get(key) ?: return null
        return if (!node.isNull) node else null
    }

    /**
     * Kotlin extension alias of [getElement].
     */
    fun ObjectNode.elm(key: String): JsonNode? = getElement(this, key)

    /**
     * Convenience extension returning `true` when the object node contains at least one property.
     *
     * This is purely a readability helper over Jackson's `isEmpty` property.
     *
     * @return `true` if the node contains one or more properties
     */
    fun ObjectNode.isNotEmpty(): Boolean = !isEmpty

    /**
     * Read a string field from an object stored inside an array field.
     *
     * Expected JSON shape:
     * ```json
     * {
     *   "arrayKey": [
     *     { "subKey": "value" }
     *   ]
     * }
     * ```
     *
     * If any step fails (missing array, invalid index, non-object element, missing sub-field,
     * explicit null), the method returns [def].
     *
     * @param obj source object
     * @param arrayKey name of the array property
     * @param index zero-based array index
     * @param subKey property to read from the indexed object element
     * @param def default value
     * @return extracted string or [def]
     */
    fun getStringFromArrayObj(
        obj: ObjectNode,
        arrayKey: String,
        index: Int,
        subKey: String,
        def: String? = null
    ): String? {
        val arr = getArray(obj, arrayKey) ?: return def
        if (index !in 0 until arr.size()) return def

        val node = arr[index].asObjectOpt().orElse(null) ?: return def
        return getString(node, subKey, def)
    }

    /**
     * Search an array of objects for the first element whose [matchKey] equals [matchValue], then
     * return the value of [targetKey] from that same object.
     *
     * This is useful for JSON payloads where a list acts like a small lookup table.
     *
     * Example shape:
     * ```json
     * {
     *   "authors": [
     *     { "role": "main", "name": "Alice" },
     *     { "role": "guest", "name": "Bob" }
     *   ]
     * }
     * ```
     *
     * Calling with `matchKey = "role"`, `matchValue = "main"`, `targetKey = "name"` returns
     * `"Alice"`.
     *
     * @param obj source object
     * @param arrayKey array field to search
     * @param matchKey property name used as the predicate
     * @param matchValue expected property value
     * @param targetKey property to return from the matched object
     * @param def default value when no match is found
     * @return extracted string or [def]
     */
    fun findStringInArrayByKey(
        obj: ObjectNode,
        arrayKey: String,
        matchKey: String,
        matchValue: String,
        targetKey: String,
        def: String? = null
    ): String? {
        val arr = getArray(obj, arrayKey) ?: return def

        for (e in arr) {
            val item = e.asObjectOpt().orElse(null) ?: continue
            val mk = getString(item, matchKey, null)
            if (mk == matchValue) {
                return getString(item, targetKey, def)
            }
        }
        return def
    }

    /**
     * Resolve a nested value by a lightweight dot-path expression.
     *
     * Supported syntax:
     * - `a.b.c`
     * - `items[0].name`
     * - `a.b[2].value`
     *
     * Rules:
     * - Empty paths return `null`.
     * - Any missing segment, invalid index, or structural mismatch returns `null`.
     * - A final JSON `null` or missing node is normalized to `null`.
     *
     * This helper is intentionally permissive and is best suited for exploratory or convenience
     * access. For strict validation, prefer Jackson APIs such as `required(...)` / `requiredAt(...)`.
     *
     * @param obj root object
     * @param path dot-path expression
     * @return resolved node, or `null` when the path cannot be followed
     */
    fun getByPath(obj: ObjectNode, path: String): JsonNode? {
        if (path.isEmpty()) return null

        var current: JsonNode = obj
        val parts = path.split('.')

        for (part in parts) {
            if (current.isNull || current.isMissingNode) return null

            val lb = part.indexOf('[')
            if (lb >= 0) {
                val key = part.substring(0, lb)
                val rb = part.indexOf(']', lb)
                if (rb < 0) return null

                val idx = part.substring(lb + 1, rb).toIntOrNull() ?: return null
                val curObj = current.asObjectOpt().orElse(null) ?: return null
                val arr = getArray(curObj, key) ?: return null
                if (idx !in 0 until arr.size()) return null

                current = arr[idx]
            } else {
                val curObj = current.asObjectOpt().orElse(null) ?: return null
                current = curObj.get(part) ?: return null
            }
        }

        return if (current.isNull || current.isMissingNode) null else current
    }

    /**
     * Resolve a nested value by path and coerce it to a string.
     *
     * The method returns [def] when the path does not resolve, resolves to a non-value node
     * (object/array), or resolves to JSON `null`.
     *
     * @param obj root object
     * @param path dot-path expression accepted by [getByPath]
     * @param def default value
     * @return extracted string or [def]
     */
    @JvmStatic
    fun getStringByPath(obj: ObjectNode, path: String, def: String? = null): String? {
        val node = getByPath(obj, path) ?: return def
        return if (node.isValueNode) node.asString() else def
    }

}
