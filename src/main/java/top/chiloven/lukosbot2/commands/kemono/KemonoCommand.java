package top.chiloven.lukosbot2.commands.kemono;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.kemono.model.*;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.CompressUtils;
import top.chiloven.lukosbot2.util.DownloadUtils;
import top.chiloven.lukosbot2.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@org.springframework.stereotype.Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "kemono",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class KemonoCommand implements IBotCommand {
    private static final StringUtils SU = StringUtils.getStringUtils();
    private static final KemonoAPI KAPI = KemonoAPI.getKemonoAPI();

    private String resolveKemonoFileUrl(String path) {
        String p = SU.replaceNull(path, "").trim();

        if (p.isEmpty()) return "";
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (!p.startsWith("/")) p = "/" + p;

        return "https://kemono.cr" + p;
    }

    @Override
    public String name() {
        return "kemono";
    }

    @Override
    public String description() {
        return "从 kemono.cr 获取内容";
    }

    @Override
    public String usage() {
        return """
                用法：
                /kemono
                  - view
                    - post <post_url> # 查询 post 信息
                    - post <service> <creator_id> <post_id> # 通过 kemono.cr 上的信息查询
                    - post <service> <platform_post_id> # 通过原站 post id 解析到 kemono post
                    - creator <creator_url> # 查询 creator 与其 post 的信息
                    - creator <service> <creator_id> # 通过 kemono.cr 上的信息查询
                  - archive
                    - post <post_url> # 打包下载 post 内容
                    - post <service> <creator_id> <post_id> # 通过 kemono.cr 上的信息下载
                    - post <service> <platform_post_id> # 通过原站 post id 解析到 kemono post
                    - creator <creator_url> # 打包下载 creator 的全部内容
                    - creator <service> <creator_id>
                  - hashsearch <sha_256>
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
                        .then(literal("view")
                                .then(literal("post")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> executeCommand("view_post", ctx))))
                                .then(literal("creator")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> executeCommand("view_creator", ctx)))))
                        .then(literal("archive")
                                .then(literal("post")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> executeCommand("archive_post", ctx))))
                                .then(literal("creator")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> executeCommand("archive_creator", ctx)))))
                        .then(literal("hashsearch")
                                .then(argument("sha_256", StringArgumentType.greedyString())
                                        .executes(ctx -> executeCommand("hash_search", ctx))))
        );
    }

    private int executeCommand(String type, CommandContext<CommandSource> ctx) {
        try {
            String rawArgs = StringArgumentType.getString(ctx, "args");
            String a = SU.replaceNull(rawArgs, "").trim();

            String result = switch (type) {
                case "view_post" -> viewPost(a);
                case "view_creator" -> viewCreator(a);
                case "archive_post" -> archivePost(a, ctx.getSource());
                case "archive_creator" -> archiveCreator(a, ctx.getSource());
                case "hash_search" -> hashSearch(a);
                default -> "";
            };

            ctx.getSource().reply(result);
            return 1;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404")) {
                ctx.getSource().reply("发生错误：资源未找到，请检查你的输入内容。");
            } else {
                log.warn(e.getMessage(), e);
                ctx.getSource().reply("发生错误：" + SU.replaceNull(e.getMessage()));
            }
            return 0;
        }
    }

    private String viewPost(String args) throws IOException {
        ResolvedPost rp = resolvePost(args);
        return Post.fromSpecific(KAPI.getSpecificPost(rp.service, rp.creatorId, rp.postId)).getSpecific();
    }

    private String viewCreator(String args) throws IOException {
        ParsedCreator c = parseCreatorArgs(args);
        var profile = KAPI.getCreatorProfile(c.service, c.creatorId);
        var posts = KAPI.getCreatorPosts(c.service, c.creatorId);
        return Creator.fromProfileAndPosts(profile, posts).getString();
    }

    private String archivePost(String args, CommandSource src) throws IOException {
        ResolvedPost rp = resolvePost(args);

        JsonObject root = KAPI.getSpecificPost(rp.service, rp.creatorId, rp.postId);
        ArchivePlan plan = buildArchivePlanForPost(rp.service, rp.creatorId, rp.postId, root);

        return executeArchivePlanAndZip(
                plan,
                "post_%s_%s_%s".formatted(rp.service, rp.creatorId, rp.postId),
                src
        );
    }

    private String archiveCreator(String args, CommandSource src) throws IOException {
        ParsedCreator c = parseCreatorArgs(args);

        ArchivePlan plan = buildArchivePlanForCreator(c.service, c.creatorId);

        return executeArchivePlanAndZip(
                plan,
                "creator_%s_%s".formatted(c.service, c.creatorId),
                src
        );
    }

    private String hashSearch(String sha) throws IOException {
        String s = SU.replaceNull(sha, "").trim();
        if (s.isEmpty()) return "参数为空。";
        return File.fromJsonObject(KAPI.getFileFromHash(s)).getString();
    }

    private ResolvedPost resolvePost(String args) throws IOException {
        ParsedPost p = parsePostArgs(args);

        if (p.creatorId != null && p.postId != null) {
            log.debug("Post resolved (direct): service={}, creatorId={}, postId={}", p.service, p.creatorId, p.postId);
            return new ResolvedPost(p.service, p.creatorId, p.postId);
        }

        var map = KAPI.getPostFromServicePost(p.service, p.servicePostId);
        String creatorId = map.get("artist_id").getAsString();
        String postId = map.get("post_id").getAsString();

        log.debug("Post resolved (servicePost): service={}, creatorId={}, postId={}, servicePostId={}",
                p.service, creatorId, postId, p.servicePostId);

        return new ResolvedPost(p.service, creatorId, postId);
    }

    private ParsedPost parsePostArgs(String args) {
        String a = SU.replaceNull(args, "").trim();
        if (a.isEmpty()) throw new IllegalArgumentException("参数为空。");

        String[] parts = a.split("\\s+");

        if (parts.length == 1 && SU.isUrl(parts[0])) return parsePostUrl(parts[0]);

        if (parts.length >= 3) {
            Service service = Service.getService(parts[0]);
            if (service == Service.UNKNOWN) throw new IllegalArgumentException("未知的 service：" + parts[0]);
            log.debug("Post parsed (specific): service={}, creatorId={}, postId={}", service, parts[1], parts[2]);
            return ParsedPost.specific(service, parts[1], parts[2]);
        }

        if (parts.length == 2) {
            Service service = Service.getService(parts[0]);
            if (service == Service.UNKNOWN) throw new IllegalArgumentException("未知的 service：" + parts[0]);
            log.debug("Post parsed (servicePost): service={}, servicePostId={}", service, parts[1]);
            return ParsedPost.servicePost(service, parts[1]);
        }

        throw new IllegalArgumentException("参数格式错误：post <post_url> 或 post <service> <creator_id> <post_id> 或 post <service> <platform_post_id>");
    }

    private ParsedPost parsePostUrl(String url) {
        URI u = URI.create(url);

        String host = SU.replaceNull(u.getHost(), "").toLowerCase();
        List<String> ss = SU.splitPath(SU.replaceNull(u.getPath(), ""));

        if (host.endsWith("kemono.cr")) {
            if (ss.size() >= 5 && "user".equals(ss.get(1)) && "post".equals(ss.get(3))) {
                Service service = Service.getService(ss.get(0));
                if (service == Service.UNKNOWN) throw new IllegalArgumentException("未知的 service：" + ss.get(0));
                log.debug("Post parsed (kemono url): service={}, creatorId={}, postId={}", service, ss.get(2), ss.get(4));
                return ParsedPost.specific(service, ss.get(2), ss.get(4));
            }
            throw new IllegalArgumentException("无法识别的 kemono post_url: " + url);
        }

        Service.ServiceAndPostId sp = Service.parseServicePostUrl(u);
        if (sp.service() == Service.UNKNOWN) throw new IllegalArgumentException("未知的平台链接：" + url);

        log.debug("Post parsed (platform url): service={}, servicePostId={}", sp.service(), sp.servicePostId());
        return ParsedPost.servicePost(sp.service(), sp.servicePostId());
    }

    private ParsedCreator parseCreatorArgs(String args) {
        String a = SU.replaceNull(args, "").trim();
        if (a.isEmpty()) throw new IllegalArgumentException("参数为空。");

        String[] parts = a.split("\\s+");
        if (parts.length == 1 && SU.isUrl(parts[0])) return parseCreatorUrl(parts[0]);

        if (parts.length >= 2) {
            Service service = Service.getService(parts[0]);
            if (service == Service.UNKNOWN) throw new IllegalArgumentException("未知的 service：" + parts[0]);
            String creatorId = parts[1];
            log.debug("Creator parsed: service={}, creatorId={}", service, creatorId);
            return new ParsedCreator(service, creatorId);
        }

        throw new IllegalArgumentException("参数格式错误：creator <creator_url> 或 creator <service> <creator_id>");
    }

    private ParsedCreator parseCreatorUrl(String url) {
        URI u = URI.create(url);
        String host = SU.replaceNull(u.getHost(), "").toLowerCase();
        if (!host.endsWith("kemono.cr")) throw new IllegalArgumentException("无法识别的 creator_url: " + url);

        List<String> ss = SU.splitPath(SU.replaceNull(u.getPath(), ""));
        if (ss.size() >= 3 && "user".equals(ss.get(1))) {
            Service service = Service.getService(ss.get(0));
            if (service == Service.UNKNOWN) throw new IllegalArgumentException("未知的 service：" + ss.get(0));
            String creatorId = ss.get(2);
            log.debug("Creator parsed (kemono url): service={}, creatorId={}", service, creatorId);
            return new ParsedCreator(service, creatorId);
        }

        throw new IllegalArgumentException("无法识别的 creator_url: " + url);
    }

    private ArchivePlan buildArchivePlanForCreator(Service service, String creatorId) throws IOException {
        JsonArray posts = KAPI.getCreatorPosts(service, creatorId);
        ArchivePlan plan = new ArchivePlan(service, creatorId, null);

        for (JsonElement element : posts) {
            String postId = Optional.ofNullable(element)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .map(p -> p.get("id"))
                    .filter(id -> !id.isJsonNull())
                    .map(JsonElement::getAsString)
                    .filter(id -> !id.isBlank())
                    .orElse(null);

            if (postId == null) continue;

            JsonObject root = KAPI.getSpecificPost(service, creatorId, postId);
            ArchivePlan postPlan = buildArchivePlanForPost(service, creatorId, postId, root);

            postPlan.items.stream()
                    .map(it -> new DownloadItem(postId + "__" + it.name(), it.path()))
                    .forEach(plan.items::add);
        }

        return plan;
    }

    private String executeArchivePlanAndZip(ArchivePlan plan, String hint, CommandSource src) throws IOException {
        if (plan == null || plan.items.isEmpty()) return "没有可下载的附件。";

        String id = "%s_%s_%s".formatted(hint, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")), UUID.randomUUID().toString().substring(0, 8));
        Path base = getArchiveBaseDir();
        Path workDir = Files.createDirectories(base.resolve(id));
        Path zipFile = base.resolve(id + ".zip");

        try {
            List<DownloadUtils.NamedUrl> tasks = plan.items.stream()
                    .filter(Objects::nonNull)
                    .map(it -> {
                        String name = SU.replaceNull(it.name()).trim();
                        String path = SU.replaceNull(it.path()).trim();
                        if (name.isEmpty() || path.isEmpty()) return null;
                        try {
                            return new DownloadUtils.NamedUrl(name, URI.create(path));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            src.reply("解析完成，正在下载 %s 个文件……".formatted(tasks.size()));
            DownloadUtils.BatchResult br = DownloadUtils.downloadAllToDirConcurrent(
                    tasks,
                    workDir,
                    null,
                    300_000,
                    8,
                    2
            );
            // TODO: compress file need to be separated, telegram has a max size of 50MB, fuck you telegram
            // TODO: discord have bug when sending files
            src.reply("文件已下载完成，正在打包……");
            CompressUtils.zipDirectory(workDir, zipFile);

            byte[] bytes = Files.readAllBytes(zipFile);
            src.replyFileBytes(zipFile.getFileName().toString(), bytes, "application/zip");

            String result = """
                    打包下载完成：成功下载 %s 个文件，共 %s 个。
                    压缩包：%s（%s）
                    """.formatted(br.ok(), tasks.size(), zipFile.getFileName(), SU.formatFileSize(Files.size(zipFile)));
            return br.failed().isEmpty() ? result : result + "。下载失败：" + String.join(", ", br.failed());
        } finally {
            if (Files.exists(workDir)) {
                try (var stream = Files.walk(workDir)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException _) {
                        }
                    });
                } catch (IOException _) {
                }
            }
            Files.deleteIfExists(zipFile);
        }
    }

    private ArchivePlan buildArchivePlanForPost(Service service, String creatorId, String postId, JsonObject root) {
        ArchivePlan plan = new ArchivePlan(service, creatorId, postId);

        Post post = Post.fromSpecific(root);

        if (post.attachments() != null) {
            for (Attachment a : post.attachments()) {
                if (a == null) continue;

                String name = SU.replaceNull(a.fileName(), "").trim();
                String path = SU.replaceNull(a.path(), "").trim();
                if (name.isEmpty() || path.isEmpty()) continue;

                String url = resolveKemonoFileUrl(path);
                plan.items.add(new DownloadItem(name, url));
            }
        }

        return plan;
    }

    private Path getArchiveBaseDir() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "lukosBot2");
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    private record ParsedCreator(Service service, String creatorId) {
    }

    private record ParsedPost(Service service, String creatorId, String postId, String servicePostId) {
        static ParsedPost specific(Service service, String creatorId, String postId) {
            return new ParsedPost(service, creatorId, postId, null);
        }

        static ParsedPost servicePost(Service service, String servicePostId) {
            return new ParsedPost(service, null, null, servicePostId);
        }
    }

    private record ResolvedPost(Service service, String creatorId, String postId) {
    }

    private static class ArchivePlan {
        final Service service;
        final String creatorId;
        final String postId;
        final List<DownloadItem> items = new ArrayList<>();

        ArchivePlan(Service service, String creatorId, String postId) {
            this.service = service;
            this.creatorId = creatorId;
            this.postId = postId;
        }
    }

    private record DownloadItem(String name, String path) {
    }
}
