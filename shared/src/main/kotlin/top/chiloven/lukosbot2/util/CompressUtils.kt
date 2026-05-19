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
        PathUtils.ensureParentDirectories(zipFile)
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
        PathUtils.ensureParentDirectories(zipFile)
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
        PathUtils.ensureParentDirectories(zipFile)
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
            val normalizedEntryName = PathUtils.uniqueRelativeEntryName(item.entryName, usedNames)
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

    data class NamedPath(
        val entryName: String,
        val path: Path,
    )

}
