package top.chiloven.lukosbot2.commands.kemono;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.kemono.model.Creator;
import top.chiloven.lukosbot2.commands.kemono.model.File;
import top.chiloven.lukosbot2.commands.kemono.model.Post;
import top.chiloven.lukosbot2.commands.kemono.model.Service;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.SHAUtil;
import top.chiloven.lukosbot2.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
    private static final SHAUtil SHAU = SHAUtil.getSHAUtil();
    private static final KemonoAPI KAPI = KemonoAPI.getKemonoAPI();

    private static final URI API = URI.create("https://kemono.cr/api");
    private static final URI RES_PATH = URI.create("https://kemono.cr");

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
                    - creator <creator_url> # 查询 creator 与其 post 的信息
                    - creator <service> <creator_id> [page] # 通过 kemono.cr 上的信息查询
                  - archive
                    - post <post_url> # 打包下载 post 内容
                    - post <service> <creator_id> <post_id> # 通过 kemono.cr 上的信息下载
                    - creator <creator_url> # 打包下载 creator 的全部内容
                    - creator <service> <creator_id>
                  - hashsearch [sha_256]
                示例
                  - /kemono
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
                                                .executes(ctx -> {
                                                    try {
                                                        String args = StringArgumentType.getString(ctx, "args");
                                                        ctx.getSource().reply(run("view_post", args));
                                                        return 1;
                                                    } catch (ResourceNotFoundException e) {
                                                        ctx.getSource().reply(e.getMessage());
                                                        return 0;
                                                    } catch (Exception e) {
                                                        log.warn(e.getMessage(), e);
                                                        ctx.getSource().reply("发生错误：" + SU.replaceNull(e.getMessage()));
                                                        return 0;
                                                    }
                                                })))
                                .then(literal("creator")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    try {
                                                        String args = StringArgumentType.getString(ctx, "args");
                                                        ctx.getSource().reply(run("view_creator", args));
                                                        return 1;
                                                    } catch (ResourceNotFoundException e) {
                                                        ctx.getSource().reply(e.getMessage());
                                                        return 0;
                                                    } catch (Exception e) {
                                                        log.warn(e.getMessage(), e);
                                                        ctx.getSource().reply("发生错误：" + SU.replaceNull(e.getMessage()));
                                                        return 0;
                                                    }
                                                }))))
                        .then(literal("archive")
                                .then(literal("post")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    try {
                                                        String args = StringArgumentType.getString(ctx, "args");
                                                        ctx.getSource().reply(run("archive_post", args));
                                                        return 1;
                                                    } catch (ResourceNotFoundException e) {
                                                        ctx.getSource().reply(e.getMessage());
                                                        return 0;
                                                    } catch (Exception e) {
                                                        log.warn(e.getMessage(), e);
                                                        ctx.getSource().reply("发生错误：" + SU.replaceNull(e.getMessage()));
                                                        return 0;
                                                    }
                                                })))
                                .then(literal("creator")
                                        .then(argument("args", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    try {
                                                        String args = StringArgumentType.getString(ctx, "args");
                                                        ctx.getSource().reply(run("archive_creator", args));
                                                        return 1;
                                                    } catch (ResourceNotFoundException e) {
                                                        ctx.getSource().reply(e.getMessage());
                                                        return 0;
                                                    } catch (Exception e) {
                                                        log.warn(e.getMessage(), e);
                                                        ctx.getSource().reply("发生错误：" + SU.replaceNull(e.getMessage()));
                                                        return 0;
                                                    }
                                                }))))
                        .then(literal("hashsearch")
                                .then(argument("sha_256", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            try {
                                                String sha = StringArgumentType.getString(ctx, "sha_256");
                                                ctx.getSource().reply(run("hash_search", sha));
                                                return 1;
                                            } catch (ResourceNotFoundException e) {
                                                ctx.getSource().reply(e.getMessage());
                                                return 0;
                                            } catch (Exception e) {
                                                log.warn(e.getMessage(), e);
                                                ctx.getSource().reply("发生错误：" + e);
                                                return 0;
                                            }
                                        })))
        );
    }

    private String run(String type, String args) throws ResourceNotFoundException, IOException {
        try {
            String a = SU.replaceNull(args, "").trim();

            return switch (type) {
                case "view_post" -> viewPost(a);
                case "view_creator" -> viewCreator(a);
                case "archive_post" -> archivePost(a);
                case "archive_creator" -> archiveCreator(a);
                case "hash_search" -> hashSearch(a);
                case null, default -> "";
            };
        } catch (IOException e) {
            String msg = e.getMessage();

            if (msg != null && msg.startsWith("HTTP 404")) {
                throw new ResourceNotFoundException("资源未找到，请检查你的输入内容。");
            }
            throw e;
        }
    }

    private String viewPost(String args) throws IOException {
        ResolvedPost rp = resolvePost(args);
        return Post.fromJsonObject(KAPI.getSpecificPost(rp.service, rp.creatorId, rp.postId)).getString();
    }

    private String viewCreator(String args) throws IOException {
        ParsedCreator c = parseCreatorArgs(args);
        var profile = KAPI.getCreatorProfile(c.service, c.creatorId);
        var posts = KAPI.getCreatorPosts(c.service, c.creatorId);
        return Creator.fromProfileAndPosts(profile, posts).getString();
    }

    private String archivePost(String args) throws IOException {
        ResolvedPost rp = resolvePost(args);
        return archivePostFromSpecific(rp.service, rp.creatorId, rp.postId);
    }

    private String archiveCreator(String args) throws IOException {
        ParsedCreator c = parseCreatorArgs(args);
        return archiveCreatorFromSpecific(c.service, c.creatorId);
    }

    private String hashSearch(String sha) throws IOException {
        String s = SU.replaceNull(sha, "").trim();
        if (s.isEmpty()) return "参数为空。";
        return File.fromJsonObject(KAPI.getFileFromHash(s)).getString();
    }

    private ParsedPost parsePostArgs(String args) {
        String a = SU.replaceNull(args, "").trim();
        if (a.isEmpty()) throw new IllegalArgumentException("参数为空。");

        String[] parts = a.split("\\s+");

        if (parts.length == 1 && isUrl(parts[0])) return parsePostUrl(parts[0]);

        if (parts.length >= 3) {
            Service service = Service.getService(parts[0]);
            log.debug("Post parsed: service={}, creatorId={}, postId={}", service, parts[1], parts[2]);
            return ParsedPost.specific(service, parts[1], parts[2]);
        }

        if (parts.length == 2) {
            Service service = Service.getService(parts[0]);
            log.debug("Post parsed: service={}, servicePostId={}", service, parts[1]);
            return ParsedPost.servicePost(service, parts[1]);
        }

        throw new IllegalArgumentException("参数格式错误：post <post_url> 或 post <service> <creator_id> <post_id> 或 post <service> <platform_post_id>");
    }

    private ParsedCreator parseCreatorArgs(String args) {
        String a = SU.replaceNull(args, "").trim();
        if (a.isEmpty()) throw new IllegalArgumentException("参数为空。");

        String[] parts = a.split("\\s+");
        if (parts.length == 1 && isUrl(parts[0])) return parseCreatorUrl(parts[0]);

        if (parts.length >= 2) {
            Service service = Service.getService(parts[0]);
            String creatorId = parts[1];
            log.debug("Creator parsed: service={}, creatorId={}", service, creatorId);
            return new ParsedCreator(service, creatorId);
        }

        throw new IllegalArgumentException("参数格式错误：creator <creator_url> 或 creator <service> <creator_id> [page]");
    }

    private ParsedPost parsePostUrl(String url) {
        URI u = URI.create(url);

        String host = SU.replaceNull(u.getHost(), "").toLowerCase();
        String path = SU.replaceNull(u.getPath(), "");
        List<String> ss = splitPath(path);

        if (host.endsWith("kemono.cr")) {
            if (ss.size() >= 5 && "user".equals(ss.get(1)) && "post".equals(ss.get(3))) {
                Service service = Service.getService(ss.get(0));
                log.debug("Post parsed: service={}, creatorId={}, postId={}", service, ss.get(2), ss.get(4));
                return ParsedPost.specific(service, ss.get(2), ss.get(4));
            }
            throw new IllegalArgumentException("无法识别的 kemono post_url: " + url);
        }

        Service.ServiceAndPostId sp = Service.parseServicePostUrl(u);

        log.debug("Post parsed: service={}, servicePostId={}", sp.service(), sp.servicePostId());
        return ParsedPost.servicePost(sp.service(), sp.servicePostId());
    }

    private List<String> splitPath(String path) {
        String[] seg = path.split("/");
        List<String> ss = new ArrayList<>();
        for (String s : seg) if (!s.isBlank()) ss.add(s);
        return ss;
    }

    private ParsedCreator parseCreatorUrl(String url) {
        URI u = URI.create(url);
        String[] seg = u.getPath().split("/");
        List<String> ss = new ArrayList<>();
        for (String s : seg) if (!s.isBlank()) ss.add(s);

        if (ss.size() >= 3 && "user".equals(ss.get(1))) {
            Service service = Service.getService(ss.get(0));
            String creatorId = ss.get(2);
            return new ParsedCreator(service, creatorId);
        }

        throw new IllegalArgumentException("无法识别的 creator_url: " + url);
    }

    private boolean isUrl(String s) {
        String t = SU.replaceNull(s, "").trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    // TODO: archive download feature

    @Override
    public boolean isVisible() {
        return false;
    }

    private String archivePostFromSpecific(Service service, String creatorId, String postId) throws IOException {
        throw new UnsupportedOperationException("未实现");
    }

    private String archivePostFromServicePost(Service service, String servicePostId) throws IOException {
        throw new UnsupportedOperationException("未实现");
    }

    private String archiveCreatorFromSpecific(Service service, String creatorId) throws IOException {
        throw new UnsupportedOperationException("未实现");
    }

    private ResolvedPost resolvePost(String args) throws IOException {
        ParsedPost p = parsePostArgs(args);

        if (p.creatorId != null && p.postId != null) {
            return new ResolvedPost(p.service, p.creatorId, p.postId);
        }

        String servicePostId = p.servicePostId;
        var map = KAPI.getPostFromServicePost(p.service, servicePostId);

        String creatorId = map.get("artist_id").getAsString();
        String postId = map.get("post_id").getAsString();

        log.debug("Post resolved: service={}, creatorId={}, postId={}", p.service, creatorId, postId);
        return new ResolvedPost(p.service, creatorId, postId);
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
}
