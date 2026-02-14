package top.chiloven.lukosbot2.util;

import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.Execs;
import top.chiloven.lukosbot2.util.spring.SpringBeans;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public final class DownloadUtils {

    /**
     * 批量下载：默认最多同时下载多少个文件（建议 4~16 之间按网络情况调）
     */
    public static final int DEFAULT_MAX_CONCURRENT_FILES = 8;

    /**
     * 单文件分块：默认并发 Range 连接数（建议 2~8 之间）
     */
    public static final int DEFAULT_CHUNK_THREADS = 4;

    /**
     * 小于该大小就不分块（分块有额外请求开销）
     */
    public static final long DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES = 8L * 1024 * 1024; // 8 MiB

    /**
     * 每个分块最小大小，用于限制分块数量（避免分得太碎）
     */
    public static final long DEFAULT_MIN_PART_SIZE_BYTES = 2L * 1024 * 1024; // 2 MiB

    /**
     * 默认最大重试次数（0 表示不重试；总尝试次数 = 1 + maxRetries）
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 指数退避基础延迟（毫秒）
     */
    public static final long DEFAULT_RETRY_BASE_DELAY_MS = 350;

    /**
     * 指数退避最大延迟（毫秒）
     */
    public static final long DEFAULT_RETRY_MAX_DELAY_MS = 8_000;

    /**
     * Retry-After 最大等待（毫秒），避免被服务端写个很大的值卡死线程
     */
    public static final long DEFAULT_RETRY_AFTER_CAP_MS = 30_000;

    /**
     * debug 进度日志间隔（毫秒）
     */
    public static final long DEFAULT_PROGRESS_LOG_INTERVAL_MS = 1_000;

    private static final HttpClient CLIENT = SpringBeans.getBean(ProxyConfigProp.class).applyTo(
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
    ).build();

    private static final int BUF_SIZE = 64 * 1024;

    private DownloadUtils() {
    }

    /**
     * 解析下载 URL：
     * - path 是完整 URL => 直接用
     * - path 是 /xx/yy => server.resolve(path)
     * - path 是 xx/yy  => server.resolve("/" + path)
     */
    public static URI resolveUrl(URI server, String pathOrUrl) {
        Objects.requireNonNull(server, "server");
        String p = Objects.requireNonNull(pathOrUrl, "pathOrUrl").trim();

        if (p.startsWith("http://") || p.startsWith("https://")) return URI.create(p);
        if (p.startsWith("/")) return server.resolve(p);

        return server.resolve("/" + p);
    }

    /**
     * 批量下载到同一目录（串行）
     * - 单个失败不会中断整体
     * - 返回成功数 + 失败文件名列表
     */
    public static BatchResult downloadAllToDir(
            List<NamedUrl> items,
            Path dir,
            @Nullable Map<String, String> headers,
            int timeoutMs
    ) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Files.createDirectories(dir);

        int ok = 0;
        List<String> failed = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            return new BatchResult(0, failed);
        }

        log.debug("[DL-BATCH] Downloading {} items to {}", items.size(), dir);
        items.forEach(it -> log.debug("[DL-BATCH] item={}", it));

        for (NamedUrl it : items) {
            if (it == null) continue;

            String name = (it.name() == null) ? "" : it.name().trim();
            URI url = it.url();

            if (name.isEmpty() || url == null) {
                failed.add(name.isEmpty() ? "file" : name);
                continue;
            }

            try {
                downloadToDir(url, dir, name, headers, timeoutMs);
                ok++;
            } catch (Exception ex) {
                failed.add(name);
                log.warn("[DL-BATCH] Download failed: name={}, url={}, err={}", name, url, ex.toString());
            }
        }

        log.debug("[DL-BATCH] Done: ok={}, failed={}", ok, failed.size());
        return new BatchResult(ok, failed);
    }

    public static BatchResult downloadAllToDirConcurrent(
            List<NamedUrl> items,
            Path dir,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxConcurrentFiles
    ) throws IOException {
        return downloadAllToDirConcurrent(items, dir, headers, timeoutMs, maxConcurrentFiles, 1, DEFAULT_MAX_RETRIES);
    }

    public static BatchResult downloadAllToDirConcurrent(
            List<NamedUrl> items,
            Path dir,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxConcurrentFiles,
            int chunkThreadsPerFile
    ) throws IOException {
        return downloadAllToDirConcurrent(items, dir, headers, timeoutMs, maxConcurrentFiles, chunkThreadsPerFile, DEFAULT_MAX_RETRIES);
    }

    /**
     * 批量并发下载（多线程/虚拟线程），可选启用单文件分块下载 + 重试
     *
     * @param maxConcurrentFiles  同时并发下载的文件数（建议 4~16）
     * @param chunkThreadsPerFile 单文件分块并发数：<=1 表示不分块；>1 表示尝试 Range 分块（建议 2~8）
     * @param maxRetries          最大重试次数（0 表示不重试）
     */
    public static BatchResult downloadAllToDirConcurrent(
            List<NamedUrl> items,
            Path dir,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxConcurrentFiles,
            int chunkThreadsPerFile,
            int maxRetries
    ) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Files.createDirectories(dir);

        if (items == null || items.isEmpty()) {
            return new BatchResult(0, List.of());
        }

        int maxConc = Math.max(1, maxConcurrentFiles);
        int chunkThreads = Math.max(1, chunkThreadsPerFile);
        int retries = Math.max(0, maxRetries);

        Semaphore sem = new Semaphore(maxConc);

        AtomicInteger ok = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> failed = new ConcurrentLinkedQueue<>();

        log.debug("[DL-BATCH] Concurrent downloading {} items to {}, maxConc={}, chunkThreadsPerFile={}, maxRetries={}",
                items.size(), dir, maxConc, chunkThreads, retries);

        try (ExecutorService exec = Execs.newVirtualExecutor("dl-file-")) {
            List<Future<?>> futures = new ArrayList<>(items.size());

            for (NamedUrl it : items) {
                if (it == null) continue;

                String name = (it.name() == null) ? "" : it.name().trim();
                URI url = it.url();

                if (name.isEmpty() || url == null) {
                    failed.add(name.isEmpty() ? "file" : name);
                    continue;
                }

                futures.add(exec.submit(() -> {
                    boolean acquired = false;
                    try {
                        sem.acquire();
                        acquired = true;

                        long t0 = System.nanoTime();

                        if (chunkThreads > 1) {
                            downloadToDirFast(url, dir, name, headers, timeoutMs, chunkThreads, retries);
                        } else {
                            downloadToDir(url, dir, name, headers, timeoutMs, retries);
                        }

                        long dtMs = (System.nanoTime() - t0) / 1_000_000;
                        log.debug("[DL-BATCH] OK name={}, url={}, cost={}ms", name, url, dtMs);

                        ok.incrementAndGet();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        failed.add(name);
                        log.warn("[DL-BATCH] Download interrupted: name={}, url={}", name, url);
                    } catch (Exception ex) {
                        failed.add(name);
                        log.warn("[DL-BATCH] Download failed: name={}, url={}, err={}", name, url, ex.toString());
                    } finally {
                        if (acquired) sem.release();
                    }
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Batch download interrupted", e);
                } catch (ExecutionException e) {
                    throw new IOException("Batch download task crashed", e.getCause());
                }
            }
        }

        log.debug("[DL-BATCH] Done: ok={}, failed={}", ok.get(), failed.size());
        return new BatchResult(ok.get(), new ArrayList<>(failed));
    }

    public static void downloadToFile(
            URI url,
            Path targetFile,
            @Nullable Map<String, String> headers,
            int timeoutMs
    ) throws IOException {
        downloadToFile(url, targetFile, headers, timeoutMs, DEFAULT_MAX_RETRIES);
    }

    /**
     * 下载到指定文件路径（带 temp + move 原子替换）。
     * 新增：
     * - debug 速度/进度日志
     * - 对 429/5xx/408 等进行重试
     * - 同一次调用内的失败重试：优先从 .part 继续（Range + If-Range），不支持则回退重下
     *
     * @param maxRetries 最大重试次数（0 表示不重试；总尝试次数=1+maxRetries）
     */
    public static void downloadToFile(
            URI url,
            Path targetFile,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxRetries
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(targetFile, "targetFile");

        int retries = Math.max(0, maxRetries);
        int maxAttempts = 1 + retries;

        Path parent = targetFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmp = (parent != null)
                ? parent.resolve(targetFile.getFileName().toString() + ".part")
                : Paths.get(targetFile.getFileName().toString() + ".part");

        boolean success = false;

        String ifRangeToken = null;

        long totalStartNs = System.nanoTime();

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                long attemptStartNs = System.nanoTime();

                long resumeFrom = 0L;
                if (attempt > 1 && Files.exists(tmp)) {
                    try {
                        resumeFrom = Files.size(tmp);
                    } catch (Exception ignored) {
                        resumeFrom = 0L;
                    }
                }

                boolean askedResume = resumeFrom > 0;

                try {
                    if (log.isDebugEnabled()) {
                        log.debug("[DL] start attempt {}/{} url={} -> {}{}",
                                attempt, maxAttempts, url, targetFile,
                                askedResume ? (" (resume@" + resumeFrom + ")") : "");
                    }

                    HttpResponse<InputStream> resp;
                    long finalResumeFrom = resumeFrom;
                    boolean usedRange = askedResume;

                    for (; ; ) {
                        HttpRequest.Builder b = HttpRequest.newBuilder()
                                .uri(url)
                                .GET()
                                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)));

                        applyCommonHeaders(b, headers, usedRange);

                        if (usedRange) {
                            b.setHeader("Range", "bytes=" + finalResumeFrom + "-");
                            if (ifRangeToken != null && !ifRangeToken.isBlank()) {
                                b.setHeader("If-Range", ifRangeToken);
                            }
                        }

                        resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                        int code = resp.statusCode();

                        String tokenNow = pickIfRangeToken(resp.headers());
                        if (tokenNow != null) ifRangeToken = tokenNow;

                        debugResponseSummary(url, code, resp.headers(), usedRange, finalResumeFrom);

                        if (usedRange && code == 416) {
                            closeQuietly(resp.body());
                            log.debug("[DL] HTTP 416 for resume range, restart from scratch: url={}, tmp={}", url, tmp);
                            safeDelete(tmp);
                            usedRange = false;
                            finalResumeFrom = 0L;
                            continue;
                        }

                        if (usedRange && code == 200) {
                            log.debug("[DL] server ignored Range (got 200). Will restart writing from 0: url={}", url);
                            usedRange = false;
                            finalResumeFrom = 0L;
                        }

                        if (code >= 400) {
                            Long raMs = parseRetryAfterMs(resp.headers().firstValue("retry-after").orElse(null));
                            closeQuietly(resp.body());
                            throw new HttpStatusException(code, raMs, "HTTP " + code);
                        }

                        long totalSize = guessTotalSize(code, resp.headers());
                        long expectedBodyLen = resp.headers().firstValueAsLong("content-length").orElse(-1L);

                        OpenOption[] opts = (finalResumeFrom > 0)
                                ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE}
                                : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

                        long writtenThisAttempt = 0L;
                        long lastLogNs = System.nanoTime();
                        long lastLogBytes = finalResumeFrom;

                        byte[] buf = new byte[BUF_SIZE];

                        try (InputStream in = resp.body();
                             FileChannel ch = FileChannel.open(tmp, opts)) {

                            long pos = finalResumeFrom;

                            int n;
                            while ((n = in.read(buf)) != -1) {
                                ByteBuffer bb = ByteBuffer.wrap(buf, 0, n);
                                while (bb.hasRemaining()) {
                                    int w = ch.write(bb, pos);
                                    if (w <= 0) throw new IOException("FileChannel write returned " + w);
                                    pos += w;
                                    writtenThisAttempt += w;
                                }

                                if (log.isDebugEnabled()) {
                                    long now = System.nanoTime();
                                    long intervalNs = Duration.ofMillis(DEFAULT_PROGRESS_LOG_INTERVAL_MS).toNanos();
                                    if (now - lastLogNs >= intervalNs) {
                                        long deltaBytes = pos - lastLogBytes;
                                        long deltaNs = now - lastLogNs;

                                        String pct = (totalSize > 0)
                                                ? String.format(Locale.ROOT, "%.1f%%", pos * 100.0 / totalSize)
                                                : "?";

                                        log.debug("[DL] progress {} -> {}: {} / {} ({}), instSpeed={}",
                                                url, targetFile,
                                                formatBytes(pos),
                                                (totalSize > 0 ? formatBytes(totalSize) : "?"),
                                                pct,
                                                formatSpeed(deltaBytes, deltaNs));

                                        lastLogBytes = pos;
                                        lastLogNs = now;
                                    }
                                }
                            }

                            if (expectedBodyLen > 0 && writtenThisAttempt != expectedBodyLen) {
                                throw new IOException("Body length mismatch: expected=" + expectedBodyLen + ", got=" + writtenThisAttempt);
                            }

                            if (totalSize > 0) {
                                if (pos != totalSize) {
                                    throw new IOException("Final size mismatch: expectedTotal=" + totalSize + ", got=" + pos);
                                }
                            }

                            long attemptMs = (System.nanoTime() - attemptStartNs) / 1_000_000;
                            long finalBytes = pos;

                            log.debug("[DL] done url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                                    url, targetFile,
                                    formatBytes(finalBytes),
                                    attemptMs,
                                    formatSpeed(finalBytes, System.nanoTime() - attemptStartNs));
                        }

                        moveReplace(tmp, targetFile);
                        success = true;

                        long totalMs = (System.nanoTime() - totalStartNs) / 1_000_000;
                        long finalSize;
                        try {
                            finalSize = Files.size(targetFile);
                        } catch (Exception ignored) {
                            finalSize = -1L;
                        }

                        log.debug("[DL] success url={} -> {}, finalSize={}, totalCost={}ms, totalAvgSpeed={}",
                                url, targetFile,
                                (finalSize >= 0 ? formatBytes(finalSize) : "?"),
                                totalMs,
                                (finalSize >= 0 ? formatSpeed(finalSize, System.nanoTime() - totalStartNs) : "?"));

                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Request interrupted", e);
                } catch (IOException e) {
                    boolean willRetry = (attempt < maxAttempts) && isRetryableException(e);
                    long delayMs = willRetry ? computeRetryDelayMs(e, attempt) : 0L;

                    if (willRetry) {
                        log.warn("[DL] attempt {}/{} failed, will retry in {}ms: url={}, target={}, err={}",
                                attempt, maxAttempts, delayMs, url, targetFile, e.toString());
                        sleepMs(delayMs);
                        continue;
                    }

                    log.warn("[DL] failed (no more retries): url={}, target={}, err={}", url, targetFile, e.toString());
                    throw e;
                }
            }

            throw new IOException("Download failed unexpectedly (should not reach here)");
        } finally {
            if (!success) {
                safeDelete(tmp);
            }
        }
    }

    public static Path downloadToDir(
            URI url,
            Path dir,
            String fileName,
            @Nullable Map<String, String> headers,
            int timeoutMs
    ) throws IOException {
        return downloadToDir(url, dir, fileName, headers, timeoutMs, DEFAULT_MAX_RETRIES);
    }

    public static Path downloadToDir(
            URI url,
            Path dir,
            String fileName,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxRetries
    ) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Files.createDirectories(dir);

        String safeName = sanitizeFileName(fileName);
        Path target = dir.resolve(safeName);

        downloadToFile(url, target, headers, timeoutMs, maxRetries);
        return target;
    }

    public static void downloadToFileFast(
            URI url,
            Path targetFile,
            @Nullable Map<String, String> headers,
            int timeoutMs
    ) throws IOException {
        downloadToFileFast(url, targetFile, headers, timeoutMs,
                DEFAULT_CHUNK_THREADS,
                DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                DEFAULT_MIN_PART_SIZE_BYTES,
                DEFAULT_MAX_RETRIES);
    }

    public static void downloadToFileFast(
            URI url,
            Path targetFile,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int chunkThreads,
            long minSizeForChunking,
            long minPartSizeBytes
    ) throws IOException {
        downloadToFileFast(url, targetFile, headers, timeoutMs,
                chunkThreads, minSizeForChunking, minPartSizeBytes, DEFAULT_MAX_RETRIES);
    }

    /**
     * 单文件分块下载：服务器支持 Range 且文件足够大时，自动并发分块；否则回退到 downloadToFile。
     *
     * @param chunkThreads       并发分块数（建议 2~8）
     * @param minSizeForChunking 小于该大小不分块
     * @param minPartSizeBytes   每块最小大小
     * @param maxRetries         最大重试次数（0 表示不重试）
     */
    public static void downloadToFileFast(
            URI url,
            Path targetFile,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int chunkThreads,
            long minSizeForChunking,
            long minPartSizeBytes,
            int maxRetries
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(targetFile, "targetFile");

        int threads = Math.max(1, chunkThreads);
        if (threads == 1) {
            log.debug("[DL-FAST] chunkThreads<=1, fallback to single: url={}", url);
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries);
            return;
        }

        long probeStartNs = System.nanoTime();
        RangeMeta meta;
        try {
            meta = probeRangeMeta(url, headers, timeoutMs);
        } catch (Exception e) {
            log.debug("[DL-FAST] Range probe failed, fallback to single: url={}, err={}", url, e.toString());
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries);
            return;
        } finally {
            long probeMs = (System.nanoTime() - probeStartNs) / 1_000_000;
            log.debug("[DL-FAST] probe cost={}ms, url={}", probeMs, url);
        }

        log.debug("[DL-FAST] rangeMeta url={}, acceptRanges={}, length={}, ifRangeToken={}",
                url, meta.acceptRanges(), meta.length(),
                (meta.ifRangeToken() != null ? "yes" : "no"));

        if (!meta.acceptRanges() || meta.length() <= 0 || meta.length() < Math.max(1, minSizeForChunking)) {
            log.debug("[DL-FAST] not chunking (acceptRanges={}, len={}, min={}): url={}",
                    meta.acceptRanges(), meta.length(), minSizeForChunking, url);
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries);
            return;
        }

        long total = meta.length();

        long minPart = Math.max(256 * 1024L, minPartSizeBytes);
        long partsBySizeL = Math.max(1L, total / minPart);
        int partsBySize = (int) Math.min(Integer.MAX_VALUE, partsBySizeL);

        int parts = Math.max(2, Math.min(threads, partsBySize));

        Path parent = targetFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmp = (parent != null)
                ? parent.resolve(targetFile.getFileName().toString() + ".part")
                : Paths.get(targetFile.getFileName().toString() + ".part");

        boolean chunkOk = false;
        long fileStartNs = System.nanoTime();

        try {
            safeDelete(tmp);

            try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw")) {
                raf.setLength(total);
            }

            long partSize = (total + parts - 1) / parts; // ceil
            List<Callable<Void>> tasks = new ArrayList<>(parts);

            int actualParts = 0;
            for (int i = 0; i < parts; i++) {
                long start = i * partSize;
                long end = Math.min(total - 1, start + partSize - 1);
                if (start > end) break;

                final int partIndex = i + 1;
                actualParts++;

                tasks.add(() -> {
                    downloadRangeToFile(url, tmp, start, end, headers, timeoutMs, maxRetries, partIndex, meta.ifRangeToken());
                    return null;
                });
            }

            log.debug("[DL-FAST] plan url={}, total={}, parts={}, partSize~={}, tmp={}",
                    url, formatBytes(total), actualParts, formatBytes(partSize), tmp);

            try (ExecutorService exec = Execs.newVirtualExecutor("dl-part-")) {
                List<Future<Void>> futures = exec.invokeAll(tasks);
                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException e) {
                        Throwable c = e.getCause();
                        if (c instanceof IOException io) throw io;
                        throw new IOException("Part download failed", c);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }

            moveReplace(tmp, targetFile);
            chunkOk = true;

            long ms = (System.nanoTime() - fileStartNs) / 1_000_000;
            log.debug("[DL-FAST] success url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                    url, targetFile, formatBytes(total), ms, formatSpeed(total, System.nanoTime() - fileStartNs));
        } catch (IOException e) {
            long ms = (System.nanoTime() - fileStartNs) / 1_000_000;
            log.warn("[DL-FAST] chunked download failed after {}ms, will fallback to single: url={}, err={}", ms, url, e.toString());
        } finally {
            if (!chunkOk) {
                safeDelete(tmp);
            }
        }

        if (!chunkOk) {
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries);
        }
    }

    public static Path downloadToDirFast(
            URI url,
            Path dir,
            String fileName,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int chunkThreads
    ) throws IOException {
        return downloadToDirFast(url, dir, fileName, headers, timeoutMs, chunkThreads, DEFAULT_MAX_RETRIES);
    }

    public static Path downloadToDirFast(
            URI url,
            Path dir,
            String fileName,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int chunkThreads,
            int maxRetries
    ) throws IOException {
        Objects.requireNonNull(dir, "dir");
        Files.createDirectories(dir);

        String safeName = sanitizeFileName(fileName);
        Path target = dir.resolve(safeName);

        downloadToFileFast(url, target, headers, timeoutMs,
                chunkThreads,
                DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                DEFAULT_MIN_PART_SIZE_BYTES,
                maxRetries);

        return target;
    }

    private static void downloadRangeToFile(
            URI url,
            Path tmpFile,
            long start,
            long end,
            @Nullable Map<String, String> headers,
            int timeoutMs,
            int maxRetries,
            int partIndex,
            @Nullable String ifRangeToken
    ) throws IOException {
        long expected = end - start + 1;
        if (expected <= 0) return;

        int retries = Math.max(0, maxRetries);
        int maxAttempts = 1 + retries;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long t0 = System.nanoTime();
            try {
                if (log.isDebugEnabled()) {
                    log.debug("[DL-PART] start part#{} range {}-{} ({}), attempt {}/{} url={}",
                            partIndex, start, end, formatBytes(expected), attempt, maxAttempts, url);
                }

                HttpRequest.Builder b = HttpRequest.newBuilder()
                        .uri(url)
                        .GET()
                        .timeout(Duration.ofMillis(Math.max(1, timeoutMs)));

                applyCommonHeaders(b, headers, true);
                b.setHeader("Range", "bytes=" + start + "-" + end);
                if (ifRangeToken != null && !ifRangeToken.isBlank()) {
                    b.setHeader("If-Range", ifRangeToken);
                }

                HttpResponse<InputStream> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                int code = resp.statusCode();

                debugResponseSummary(url, code, resp.headers(), true, start);

                if (code >= 400) {
                    Long raMs = parseRetryAfterMs(resp.headers().firstValue("retry-after").orElse(null));
                    closeQuietly(resp.body());
                    throw new HttpStatusException(code, raMs, "HTTP " + code);
                }

                if (code != 206) {
                    closeQuietly(resp.body());
                    throw new IOException("Expected HTTP 206, got HTTP " + code);
                }

                long written = 0L;
                byte[] buf = new byte[BUF_SIZE];

                try (InputStream in = resp.body();
                     FileChannel ch = FileChannel.open(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

                    long pos = start;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        ByteBuffer bb = ByteBuffer.wrap(buf, 0, n);
                        while (bb.hasRemaining()) {
                            int w = ch.write(bb, pos);
                            if (w <= 0) throw new IOException("FileChannel write returned " + w);
                            pos += w;
                            written += w;
                        }
                    }
                }

                if (written != expected) {
                    throw new IOException("Range bytes mismatch: expected=" + expected + ", got=" + written +
                            ", range=" + start + "-" + end);
                }

                long dtNs = System.nanoTime() - t0;
                log.debug("[DL-PART] done part#{} range {}-{}, bytes={}, cost={}ms, avgSpeed={}",
                        partIndex, start, end,
                        formatBytes(written),
                        dtNs / 1_000_000,
                        formatSpeed(written, dtNs));

                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            } catch (IOException e) {
                boolean willRetry = (attempt < maxAttempts) && isRetryableException(e);
                long delayMs = willRetry ? computeRetryDelayMs(e, attempt) : 0L;

                if (willRetry) {
                    log.warn("[DL-PART] part#{} attempt {}/{} failed, retry in {}ms: url={}, err={}",
                            partIndex, attempt, maxAttempts, delayMs, url, e.toString());
                    sleepMs(delayMs);
                    continue;
                }

                log.warn("[DL-PART] part#{} failed (no more retries): url={}, err={}", partIndex, url, e.toString());
                throw e;
            }
        }

        throw new IOException("Part download failed unexpectedly (should not reach here)");
    }

    private static RangeMeta probeRangeMeta(URI url, @Nullable Map<String, String> headers, int timeoutMs) throws IOException {
        try {
            HttpRequest.Builder hb = HttpRequest.newBuilder()
                    .uri(url)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofMillis(Math.max(1, timeoutMs)));

            applyCommonHeaders(hb, headers, false);

            HttpResponse<Void> resp = CLIENT.send(hb.build(), HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();

            debugResponseSummary(url, code, resp.headers(), false, 0);

            if (code < 400) {
                long len = resp.headers().firstValueAsLong("content-length").orElse(-1L);
                boolean accept = resp.headers().firstValue("accept-ranges")
                        .map(v -> v.toLowerCase(Locale.ROOT).contains("bytes"))
                        .orElse(false);

                String token = pickIfRangeToken(resp.headers());

                if (accept && len > 0) {
                    log.debug("[DL-PROBE] HEAD says acceptRanges=true len={} url={}", formatBytes(len), url);
                    return new RangeMeta(len, true, token);
                }

                log.debug("[DL-PROBE] HEAD insufficient (acceptRanges={}, len={}) url={}",
                        accept, (len > 0 ? formatBytes(len) : "unknown"), url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } catch (Exception ignored) {
            log.debug("[DL-PROBE] HEAD failed, will try Range probe: url={}", url);
        }

        HttpRequest.Builder pb = HttpRequest.newBuilder()
                .uri(url)
                .GET()
                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)));

        applyCommonHeaders(pb, headers, true);
        pb.setHeader("Range", "bytes=0-0");

        try {
            HttpResponse<InputStream> resp = CLIENT.send(pb.build(), HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();

            debugResponseSummary(url, code, resp.headers(), true, 0);

            closeQuietly(resp.body());

            if (code == 206) {
                long total = resp.headers().firstValue("content-range")
                        .map(DownloadUtils::parseTotalFromContentRange)
                        .orElse(-1L);

                String token = pickIfRangeToken(resp.headers());

                if (total > 0) {
                    log.debug("[DL-PROBE] Range probe OK: acceptRanges=true total={} url={}", formatBytes(total), url);
                    return new RangeMeta(total, true, token);
                }

                log.debug("[DL-PROBE] Range probe OK but total unknown: url={}", url);
                return new RangeMeta(-1L, true, token);
            }

            log.debug("[DL-PROBE] Range probe not supported (code={}): url={}", code, url);
            return new RangeMeta(-1L, false, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private static void moveReplace(Path tmp, Path targetFile) throws IOException {
        try {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void applyCommonHeaders(HttpRequest.Builder b, @Nullable Map<String, String> headers, boolean forceIdentityEncoding) {
        b.setHeader("User-Agent", "LukosBot/kemono");
        b.setHeader("Accept", "*/*");

        if (forceIdentityEncoding) {
            b.setHeader("Accept-Encoding", "identity");
        }

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k == null || k.isBlank() || v == null) return;
                b.setHeader(k, v);
            });
        }
    }

    private static void debugResponseSummary(URI url, int code, java.net.http.HttpHeaders h, boolean askedRange, long rangeStart) {
        if (!log.isDebugEnabled()) return;

        String cl = h.firstValue("content-length").orElse(null);
        String cr = h.firstValue("content-range").orElse(null);
        String ar = h.firstValue("accept-ranges").orElse(null);
        String ct = h.firstValue("content-type").orElse(null);
        String etag = h.firstValue("etag").orElse(null);
        String lm = h.firstValue("last-modified").orElse(null);
        String ra = h.firstValue("retry-after").orElse(null);

        log.debug("[DL-HTTP] url={} code={} askedRange={}{} contentLength={} contentRange={} acceptRanges={} contentType={} etag={} lastModified={} retryAfter={}",
                url, code,
                askedRange,
                (askedRange ? ("(start=" + rangeStart + ")") : ""),
                (cl != null ? cl : "-"),
                (cr != null ? cr : "-"),
                (ar != null ? ar : "-"),
                (ct != null ? ct : "-"),
                (etag != null ? etag : "-"),
                (lm != null ? lm : "-"),
                (ra != null ? ra : "-"));
    }

    private static long guessTotalSize(int code, java.net.http.HttpHeaders h) {
        if (code == 206) {
            return h.firstValue("content-range")
                    .map(DownloadUtils::parseTotalFromContentRange)
                    .orElse(-1L);
        }
        return h.firstValueAsLong("content-length").orElse(-1L);
    }

    private static @Nullable String pickIfRangeToken(java.net.http.HttpHeaders h) {
        String etag = h.firstValue("etag").orElse(null);
        if (etag != null && !etag.isBlank()) return etag;

        String lm = h.firstValue("last-modified").orElse(null);
        if (lm != null && !lm.isBlank()) return lm;

        return null;
    }

    /**
     * 解析 Content-Range 里的 total，例如： "bytes 0-0/12345" -> 12345
     */
    private static long parseTotalFromContentRange(String cr) {
        if (cr == null) return -1L;
        int slash = cr.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= cr.length()) return -1L;
        String total = cr.substring(slash + 1).trim();
        if (total.isEmpty() || "*".equals(total)) return -1L;
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static boolean isRetryableStatus(int code) {
        return code == 408 || code == 429 || (code >= 500 && code <= 599);
    }

    private static boolean isRetryableException(IOException e) {
        if (e instanceof HttpStatusException hs) {
            return isRetryableStatus(hs.statusCode);
        }
        return !(e instanceof FileSystemException);// 其它 IOException 默认认为网络/连接问题可重试
    }

    private static long computeRetryDelayMs(IOException e, int attemptIndex) {

        long exp = 1L << Math.min(20, Math.max(0, attemptIndex - 1));
        long delay = Math.min(DEFAULT_RETRY_MAX_DELAY_MS, DEFAULT_RETRY_BASE_DELAY_MS * exp);

        if (e instanceof HttpStatusException hs && hs.retryAfterMs != null && hs.retryAfterMs > 0) {
            long ra = Math.min(DEFAULT_RETRY_AFTER_CAP_MS, hs.retryAfterMs);
            delay = Math.max(delay, ra);
        }

        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, delay / 3));
        return Math.min(DEFAULT_RETRY_MAX_DELAY_MS, delay + jitter);
    }

    private static @Nullable Long parseRetryAfterMs(@Nullable String v) {
        if (v == null || v.isBlank()) return null;

        try {
            long sec = Long.parseLong(v.trim());
            if (sec <= 0) return null;
            return sec * 1000L;
        } catch (NumberFormatException ignored) {
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(v.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            long ms = Duration.between(Instant.now(), zdt.toInstant()).toMillis();
            return Math.max(0L, ms);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sleepMs(long ms) throws IOException {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Sleep interrupted", ie);
        }
    }

    private static void safeDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(@Nullable InputStream in) {
        if (in == null) return;
        try (in) {
            try {
                in.readNBytes(1);
            } catch (Exception _) {
            }
        } catch (Exception _) {
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        double b = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int u = 0;
        while (b >= 1024.0 && u < units.length - 1) {
            b /= 1024.0;
            u++;
        }
        if (u == 0) return String.format(Locale.ROOT, "%d %s", bytes, units[u]);
        return String.format(Locale.ROOT, "%.2f %s", b, units[u]);
    }

    private static String formatSpeed(long bytes, long elapsedNs) {
        if (bytes < 0 || elapsedNs <= 0) return "?";
        double sec = elapsedNs / 1_000_000_000.0;
        double bps = bytes / sec;
        return formatBytes((long) bps) + "/s";
    }

    public static String sanitizeFileName(String name) {
        String n = (name == null) ? "" : name.trim();

        n = n.replace("\\", "_").replace("/", "_");
        n = n.replaceAll("[<>:\"|?*]", "_");
        n = n.replaceAll("\\p{Cntrl}", "_");

        if (n.isBlank()) n = "file";
        return n;
    }

    private static final class HttpStatusException extends IOException {
        final int statusCode;
        final @Nullable Long retryAfterMs;

        HttpStatusException(int statusCode, @Nullable Long retryAfterMs, String message) {
            super(message);
            this.statusCode = statusCode;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private record RangeMeta(long length, boolean acceptRanges, @Nullable String ifRangeToken) {
    }

    public record NamedUrl(String name, URI url) {
    }

    public record BatchResult(int ok, List<String> failed) {
    }
}
