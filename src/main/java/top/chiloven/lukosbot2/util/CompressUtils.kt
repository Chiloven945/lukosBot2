package top.chiloven.lukosbot2.util;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Log4j2
public final class CompressUtils {

    private CompressUtils() {
    }

    /**
     * 把一个目录整体打成 zip（递归）。
     * zip 内路径以 dir 作为根（不包含 dir 本身的上级路径）。
     */
    public static void zipDirectory(Path dir, Path zipFile) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(zipFile, "zipFile");

        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        Path parent = zipFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult preVisitDirectory(@NonNull Path d, @NonNull BasicFileAttributes attrs) throws IOException {
                    Path rel = dir.relativize(d);
                    if (!rel.toString().isEmpty()) {
                        String entryName = normalizeZipEntry(rel.toString()) + "/";
                        putEntryDir(zos, entryName, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                    Path rel = dir.relativize(file);
                    String entryName = normalizeZipEntry(rel.toString());
                    putEntryFile(zos, file, entryName, attrs);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.debug("Zip created: {} <- {}", zipFile, dir);
    }

    /**
     * 将一组文件打包进 zip。
     *
     * @param baseDir 用于计算相对路径；entryName = baseDir.relativize(file)
     * @param files   要打包的文件列表
     */
    public static void zipFiles(Path baseDir, List<Path> files, Path zipFile) throws IOException {
        Objects.requireNonNull(baseDir, "baseDir");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(zipFile, "zipFile");

        Path parent = zipFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (Path f : files) {
                if (f == null) continue;
                if (!Files.isRegularFile(f)) continue;

                Path rel = baseDir.relativize(f);
                String entryName = normalizeZipEntry(rel.toString());

                BasicFileAttributes attrs = Files.readAttributes(f, BasicFileAttributes.class);
                putEntryFile(zos, f, entryName, attrs);
            }
        }

        log.debug("Zip created: {} <- {} file(s)", zipFile, files.size());
    }

    /**
     * 写入单个文件到 zip，支持指定 entryName（更适合你后面做目录结构）。
     */
    public static void zipFilesWithNames(List<NamedPath> items, Path zipFile) throws IOException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(zipFile, "zipFile");

        Path parent = zipFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (NamedPath np : items) {
                if (np == null) continue;
                Path f = np.path();
                if (!Files.isRegularFile(f)) continue;

                String entryName = normalizeZipEntry(np.entryName());
                BasicFileAttributes attrs = Files.readAttributes(f, BasicFileAttributes.class);
                putEntryFile(zos, f, entryName, attrs);
            }
        }
    }

    /**
     * ZipSlip 防护 + 统一分隔符：
     * - 统一用 '/'
     * - 禁止绝对路径、禁止 '..'
     */
    private static String normalizeZipEntry(String entryName) throws IOException {
        String n = (entryName == null) ? "" : entryName.trim();
        n = n.replace("\\", "/");

        while (n.startsWith("/")) n = n.substring(1);

        if (n.isBlank()) throw new IOException("Empty zip entry name");

        if (n.contains("..")) {
            Path p = Paths.get(n).normalize();
            String pn = p.toString().replace("\\", "/");
            if (pn.startsWith("..") || pn.contains("/..")) {
                throw new IOException("Illegal zip entry name: " + entryName);
            }
            n = pn;
        }

        return n;
    }

    private static void putEntryDir(ZipOutputStream zos, String entryName, @Nullable BasicFileAttributes attrs) throws IOException {
        String safe = normalizeZipEntry(entryName.endsWith("/") ? entryName : entryName + "/");
        ZipEntry ze = new ZipEntry(safe);
        if (attrs != null) ze.setTime(attrs.lastModifiedTime().toMillis());
        zos.putNextEntry(ze);
        zos.closeEntry();
    }

    private static void putEntryFile(ZipOutputStream zos, Path file, String entryName, @Nullable BasicFileAttributes attrs) throws IOException {
        String safe = normalizeZipEntry(entryName);
        ZipEntry ze = new ZipEntry(safe);

        if (attrs != null) {
            ze.setSize(attrs.size());
            ze.setTime(attrs.lastModifiedTime().toMillis());
        }

        zos.putNextEntry(ze);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    // TODO: LZMA(2) format support

    /**
     * 你后面如果要“自定义 zip 内路径结构”，就用这个。
     * entryName 例子： "patreon/127300515/149949916/xxx.png"
     */
    public record NamedPath(String entryName, Path path) {
    }
}
