package top.chiloven.lukosbot2.util

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.*

/**
 * Filesystem and path related helpers shared across commands, features and util objects.
 *
 * The project frequently needs a few categories of low-level path operations:
 *
 * 1. Turn arbitrary titles or remote file names into safe local filenames.
 * 2. Validate relative entry paths used by downloads and zip archives.
 * 3. Deduplicate colliding entry names in a predictable `name (2).ext` style.
 * 4. Manage temporary directories and `.part` files safely.
 * 5. Delete trees and move files atomically when possible.
 *
 * This object centralizes those concerns so call sites can stay focused on business logic rather
 * than repeating path normalization rules.
 */
object PathUtils {

    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\r\n\t]""")
    private val CONTROL_CHARS = Regex("\\p{Cntrl}")

    /**
     * Sanitize an arbitrary filename into a filesystem-friendly single path segment.
     *
     * Directory separators, control characters and characters commonly rejected by desktop file
     * systems are replaced with underscores. Blank inputs fall back to [fallback].
     *
     * @param name original filename text
     * @param fallback fallback value when the sanitized result is blank
     * @param maxLength optional maximum output length; values `<= 0` disable truncation
     * @return sanitized single-segment filename
     */
    @JvmStatic
    @JvmOverloads
    fun sanitizeFileName(name: String?, fallback: String = "file", maxLength: Int = 255): String {
        var sanitized = name?.trim().orEmpty()
            .replace(ILLEGAL_FILENAME_CHARS, "_")
            .replace(CONTROL_CHARS, "_")

        if (maxLength > 0 && sanitized.length > maxLength) {
            sanitized = sanitized.take(maxLength)
        }

        sanitized = sanitized.trim().trim('.', ' ')
        if (sanitized.isBlank()) sanitized = fallback
        return sanitized
    }

    /**
     * Sanitize a value intended to become one path segment inside a larger relative path.
     *
     * @param name original path segment
     * @param fallback fallback value when the sanitized result is blank
     * @param maxLength optional maximum output length; values `<= 0` disable truncation
     * @return sanitized path segment with any remaining separators flattened
     */
    @JvmStatic
    @JvmOverloads
    fun sanitizePathSegment(name: String?, fallback: String = "file", maxLength: Int = 255): String =
        sanitizeFileName(name, fallback, maxLength)
            .replace('/', '_')
            .replace('\\', '_')

    /**
     * Normalize a user-provided relative entry path and reject traversal or blank values.
     *
     * @param entryName candidate relative entry path
     * @param label text used in exception messages
     * @return normalized forward-slash relative path
     * @throws IOException if the path is blank, malformed or escapes the base directory
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun normalizeRelativeEntryName(entryName: String?, label: String = "entry name"): String {
        var normalized = entryName?.trim().orEmpty().replace('\\', '/')
        while (normalized.startsWith('/')) {
            normalized = normalized.substring(1)
        }
        if (normalized.isBlank()) {
            throw IOException("Empty $label")
        }

        val safe = try {
            Paths.get(normalized).normalize().toString().replace('\\', '/')
        } catch (e: InvalidPathException) {
            throw IOException("Illegal $label: $entryName", e)
        }

        if (safe.isBlank() || safe == "." || safe.startsWith("..") || safe.contains("/../")) {
            throw IOException("Illegal $label: $entryName")
        }
        return safe
    }

    /**
     * Deduplicate a relative entry path within [usedNames] using `name (2).ext` style suffixes.
     *
     * @param entryName desired relative entry path
     * @param usedNames mutable set tracking already reserved normalized names
     * @return a unique normalized relative entry path
     */
    @JvmStatic
    @Throws(IOException::class)
    fun uniqueRelativeEntryName(entryName: String, usedNames: MutableSet<String>): String {
        val safe = normalizeRelativeEntryName(entryName)
        synchronized(usedNames) {
            if (usedNames.add(safe)) return safe

            val slash = safe.lastIndexOf('/')
            val dirPart = if (slash >= 0) safe.substring(0, slash + 1) else ""
            val filePart = if (slash >= 0) safe.substring(slash + 1) else safe
            val dot = filePart.lastIndexOf('.')
            val base = if (dot > 0) filePart.substring(0, dot) else filePart
            val ext = if (dot > 0) filePart.substring(dot) else ""

            var index = 2
            while (true) {
                val candidate = "$dirPart$base ($index)$ext"
                if (usedNames.add(candidate)) return candidate
                index++
            }
        }
    }

    /**
     * Split an arbitrary path string into trimmed non-blank path segments.
     */
    @JvmStatic
    fun splitPathSegments(path: String?): List<String> =
        path?.replace('\\', '/')
            ?.split('/')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    /**
     * Best-effort inference of a filename from a URL path.
     *
     * @param url source URL text
     * @return decoded trailing path segment, or `null` when unavailable
     */
    @JvmStatic
    fun inferFileNameFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = runCatching { URI(url).path }.getOrNull() ?: url
        val tail = path.substringAfterLast('/').trim()
        if (tail.isBlank()) return null
        return runCatching { URLDecoder.decode(tail, StandardCharsets.UTF_8) }.getOrDefault(tail)
            .takeIf { it.isNotBlank() }
    }

    /**
     * Create parent directories for [path] when they do not already exist.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun ensureParentDirectories(path: Path) {
        path.parent?.let(Files::createDirectories)
    }

    /**
     * Return the sibling temporary path typically used for in-progress downloads.
     */
    @JvmStatic
    @JvmOverloads
    fun tempSiblingPath(targetFile: Path, suffix: String = ".part"): Path =
        targetFile.parent?.resolve(targetFile.fileName.toString() + suffix)
            ?: Paths.get(targetFile.fileName.toString() + suffix)

    /**
     * Move a file into place, preferring an atomic replace when supported by the filesystem.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun moveReplace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Quietly delete a single path when it exists.
     */
    @JvmStatic
    fun deleteIfExistsQuietly(path: Path?) {
        if (path == null) return
        runCatching { Files.deleteIfExists(path) }
    }

    /**
     * Recursively delete a directory tree or a single file.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun deleteRecursively(path: Path?) {
        if (path == null || Files.notExists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    /**
     * Create a temporary directory, run [block], then delete the directory tree afterwards.
     */
    @JvmStatic
    inline fun <T> withTempDirectory(prefix: String, block: (Path) -> T): T {
        val dir = Files.createTempDirectory(prefix)
        try {
            return block(dir)
        } finally {
            deleteRecursively(dir)
        }
    }

}
