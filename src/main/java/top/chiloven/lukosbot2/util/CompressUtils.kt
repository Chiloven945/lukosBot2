package top.chiloven.lukosbot2.util

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipOutputStream

/**
 * Compressing related utils for creating zip files. More format support can be implemented in future.
 *
 * @author Chiloven945
 */
object CompressUtils {

    private val log = LogManager.getLogger(CompressUtils::class.java)

    @JvmStatic
    @Throws(IOException::class)
    fun zipDirectory(dir: Path, zipFile: Path) {
        if (!Files.isDirectory(dir)) {
            throw IOException("Not a directory: $dir")
        }
        prepareParent(zipFile)
        Files.deleteIfExists(zipFile)

        val files = Files.walk(dir).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .map { file -> NamedPath(dir.relativize(file).toString().replace('\\', '/'), file) }
                .toList()
        }

        zipNamedPaths(files, zipFile)
        log.debug("Zip created with zip4j: {} <- {}", zipFile, dir)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zipFiles(baseDir: Path, files: List<Path?>, zipFile: Path) {
        prepareParent(zipFile)
        Files.deleteIfExists(zipFile)

        val items = files.asSequence()
            .filterNotNull()
            .filter { path -> Files.isRegularFile(path) }
            .map { file -> NamedPath(baseDir.relativize(file).toString().replace('\\', '/'), file) }
            .toList()

        zipNamedPaths(items, zipFile)
        log.debug("Zip created with zip4j: {} <- {} file(s)", zipFile, items.size)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zipFilesWithNames(items: List<NamedPath?>, zipFile: Path) {
        prepareParent(zipFile)
        Files.deleteIfExists(zipFile)

        val actualItems = items.asSequence()
            .filterNotNull()
            .filter { Files.isRegularFile(it.path) }
            .toList()

        zipNamedPaths(actualItems, zipFile)
        log.debug("Zip created with zip4j: {} <- {} named file(s)", zipFile, actualItems.size)
    }

    @Throws(IOException::class)
    private fun zipNamedPaths(items: List<NamedPath>, zipFile: Path) {
        if (items.isEmpty()) {
            createEmptyZip(zipFile)
            return
        }

        val zip = ZipFile(zipFile.toFile())
        val usedNames = LinkedHashSet<String>()

        for (item in items) {
            val normalizedEntryName = uniqueEntryName(normalizeZipEntry(item.entryName), usedNames)
            val params = newZipParameters(normalizedEntryName)
            zip.addFile(item.path.toFile(), params)
        }
    }

    @Throws(IOException::class)
    private fun createEmptyZip(zipFile: Path) {
        Files.newOutputStream(zipFile).use { output ->
            ZipOutputStream(output, StandardCharsets.UTF_8).use { }
        }
    }

    private fun newZipParameters(entryName: String): ZipParameters =
        ZipParameters().apply {
            fileNameInZip = entryName
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
        }

    @Throws(IOException::class)
    private fun prepareParent(zipFile: Path) {
        zipFile.parent?.let(Files::createDirectories)
    }

    @Throws(IOException::class)
    private fun normalizeZipEntry(entryName: String?): String {
        var normalized = entryName?.trim().orEmpty().replace('\\', '/')

        while (normalized.startsWith('/')) {
            normalized = normalized.substring(1)
        }

        if (normalized.isBlank()) {
            throw IOException("Empty zip entry name")
        }

        normalized = Paths.get(normalized).normalize().toString().replace('\\', '/')
        if (normalized.isBlank() || normalized == "." || normalized.startsWith("..") || normalized.contains("/../")) {
            throw IOException("Illegal zip entry name: $entryName")
        }

        return normalized
    }

    private fun uniqueEntryName(entryName: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(entryName)) return entryName

        val slash = entryName.lastIndexOf('/')
        val dirPart = if (slash >= 0) entryName.substring(0, slash + 1) else ""
        val filePart = if (slash >= 0) entryName.substring(slash + 1) else entryName
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

    class NamedPath(
        val entryName: String,
        val path: Path,
    ) {

        fun entryName(): String = entryName
        fun path(): Path = path

        override fun toString(): String = "NamedPath(entryName=$entryName, path=$path)"
        override fun equals(other: Any?): Boolean =
            this === other || (other is NamedPath && entryName == other.entryName && path == other.path)

        override fun hashCode(): Int = 31 * entryName.hashCode() + path.hashCode()
    }

}
