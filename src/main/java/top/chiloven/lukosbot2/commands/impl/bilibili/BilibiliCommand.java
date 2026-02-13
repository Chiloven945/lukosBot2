package top.chiloven.lukosbot2.commands.impl.bilibili;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.JsonUtils;
import top.chiloven.lukosbot2.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /bilibili command for fetching Bilibili video info by AV/BV ID or b23 short link.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "bilibili",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class BilibiliCommand implements IBotCommand {

    public static final StringUtils su = StringUtils.getStringUtils();
    public static final JsonUtils ju = JsonUtils.getJsonUtils();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(Redirect.NEVER)
            .build();

    // bv: "BV" + 10位，常见以 1 开头，但这里放宽到字母数字
    private static final Pattern BV_PATTERN = Pattern.compile("(?i)\\bBV([0-9A-Za-z]{10})\\b");
    // av: av123456 / AV123456，允许可选 "av" 前缀，但我们仍优先匹配标准写法
    private static final Pattern AV_PATTERN = Pattern.compile("(?i)\\bAV?(\\d+)\\b");
    // b23 短链
    private static final Pattern B23_PATTERN = Pattern.compile("https?://b23\\.tv/([0-9A-Za-z]+)\\b");

    private static final String UA = "Mozilla/5.0 (compatible; LukosBot/1.0; +https://bilibili.com)";

    /**
     * Execute the /bilibili command.
     *
     * @param src      The command source.
     * @param rawInput The raw input string.
     * @param detailed Whether to show detailed info.
     * @return 1 on success, 0 on failure.
     */
    private static int run(CommandSource src, String rawInput, boolean detailed) {
        log.info("BilibiliCommand invoked by {} with input='{}', detailed={}",
                src.in().addr(), rawInput, detailed);

        String input = rawInput.trim();
        log.debug("Raw input after trim: '{}'", input);

        // 1) 归一化：若为短链则解析一次；解析失败直接回复并返回
        String normalized = input.startsWith("http")
                ? resolveB23(input)
                : input;
        if (normalized == null) {
            log.warn("Failed to resolve b23 link: {}", input);
            src.reply("无法解析短链接：" + input);
            return 0;
        }

        // 2) 识别 AV / BV：用“解析器函数列表”按序尝试，命中即停止
        record Id(String bvid, Long aid) {
        }
        List<Function<String, Optional<Id>>> resolvers = List.of(
                s -> {
                    Matcher m = BV_PATTERN.matcher(s);
                    return (m.find() ? Optional.of(new Id("BV" + m.group(1), null)) : Optional.empty());
                },
                s -> {
                    Matcher m = AV_PATTERN.matcher(s);
                    return (m.find() ? Optional.of(new Id(null, Long.parseLong(m.group(1)))) : Optional.empty());
                }
        );

        Optional<Id> id = resolvers.stream()
                .map(fn -> fn.apply(normalized))
                .flatMap(Optional::stream)
                .findFirst();

        if (id.isEmpty()) {
            src.reply("无效的视频编号（仅支持 AV/BV 或 b23 短链）");
            return 0;
        }

        // 3) 拉取并渲染
        try {
            Id i = id.get();
            log.info("Fetching Bilibili video info for bvid={}, aid={}", i.bvid(), i.aid());
            VideoInfo vi = fetchVideoInfo(i.bvid(), i.aid());
            if (vi == null) {
                log.warn("Video not found for bvid={}, aid={}", i.bvid(), i.aid());
                src.reply("未找到该视频");
                return 0;
            }

            String canonical = "https://www.bilibili.com/video/" + vi.getBvid();
            String text = detailed ? renderDetailed(vi, canonical) : renderSimple(vi, canonical);
            src.reply((vi.getCover() != null && !vi.getCover().isBlank()) ? text + "\n" + vi.getCover() : text);

            log.info("BilibiliCommand completed successfully for input='{}'", rawInput);
            return 1;
        } catch (Exception e) {
            log.error("Exception executing BilibiliCommand for input='{}'", normalized, e);
            src.reply("获取视频信息失败：" + e.getMessage());
            return 0;
        }
    }

    private static String renderSimple(VideoInfo v, String link) {
        return """
                %s
                标题：%s
                类型：%s
                UP 主：%s
                日期：%s
                详细内容：
                """.formatted(
                link,
                v.getTitle(),
                su.replaceNull(v.getTname()),
                su.replaceNull(v.getOwnerName()),
                su.formatTime(v.getPubDateMs())
        );
    }

    private static String renderDetailed(VideoInfo v, String link) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(link).append("\n");
        sb.append("标题：").append(v.getTitle());
        if (v.getPageCount() > 1) sb.append("（").append(v.getPageCount()).append("P）");
        sb.append(" | 类型：").append(su.replaceNull(v.getTname())).append("\n");

        sb.append("UP主：").append(su.replaceNull(v.getOwnerName())).append(" | 粉丝：").append(su.formatNum(v.getFans())).append("\n");

        if (v.getDesc() != null && !v.getDesc().isBlank()) {
            sb.append("简介：").append(su.truncate(v.getDesc(), 160)).append("\n");
        }

        sb.append("观看：").append(su.formatNum(v.getView()))
                .append(" | 弹幕：").append(su.formatNum(v.getDanmaku()))
                .append(" | 评论：").append(su.formatNum(v.getReply())).append("\n");

        sb.append("喜欢：").append(su.formatNum(v.getLike()))
                .append(" | 投币：").append(su.formatNum(v.getCoin()))
                .append(" | 收藏：").append(su.formatNum(v.getFavorite()))
                .append(" | 分享：").append(su.formatNum(v.getShare())).append("\n");

        sb.append("日期：").append(su.formatTime(v.getPubDateMs()));
        return sb.toString();
    }

    private static VideoInfo fetchVideoInfo(String bvid, Long aid) throws Exception {
        final String api = buildViewApi(bvid, aid);
        log.debug("Requesting Bilibili API: {}", api);

        final JsonObject root = getJson(api, 8);
        if (root == null) {
            log.error("Null JSON response from {}", api);
            return null;
        }

        final int code = ju.getInt(root, "code", -999);
        final String message = ju.getString(root, "message", "");
        log.debug("API response code={}, message='{}' for {}", code, message, api);
        if (code != 0) {
            log.warn("Non-zero API code={} (message='{}') for {}", code, message, api);
            return null;
        }

        final JsonObject data = ju.getObj(root, "data");
        if (data == null) {
            log.warn("API response has no 'data' object for {}", api);
            return null;
        }

        final VideoInfo v = new VideoInfo();
        v.setBvid(Objects.requireNonNullElse(ju.getString(data, "bvid", null), bvid));
        v.setTitle(ju.getString(data, "title", ""));
        v.setTname(ju.getString(data, "tname", ""));
        v.setDesc(ju.getString(data, "desc", ""));
        v.setCover(ju.getString(data, "pic", ""));
        v.setPubDateMs(publishDateMs(data));

        // owner
        final JsonObject owner = ju.getObj(data, "owner");
        v.setOwnerName(ju.getString(owner, "name", ""));
        v.setOwnerMid(ju.getLong(owner, "mid", 0L));

        // stat
        final JsonObject stat = ju.getObj(data, "stat");
        v.setView(ju.getLong(stat, "view", 0L));
        v.setDanmaku(ju.getLong(stat, "danmaku", 0L));
        v.setReply(ju.getLong(stat, "reply", 0L));
        v.setFavorite(ju.getLong(stat, "favorite", 0L));
        v.setCoin(ju.getLong(stat, "coin", 0L));
        v.setShare(ju.getLong(stat, "share", 0L));
        v.setLike(ju.getLong(stat, "like", 0L));

        // pages
        v.setPageCount(pageCount(data));
        log.debug("Parsed pages: pageCount={}, hasPagesArray={}, videosField={}",
                v.getPageCount(), data.has("pages"), data.has("videos"));

        log.info("Parsed video: bvid={}, aid={}, title='{}', tname='{}', owner='{}'(mid={}), view={}, danmaku={}, reply={}, like={}, coin={}, fav={}, share={}, pageCount={}",
                v.getBvid(), aid, v.getTitle(), v.getTname(), v.getOwnerName(), v.getOwnerMid(),
                v.getView(), v.getDanmaku(), v.getReply(), v.getLike(), v.getCoin(), v.getFavorite(),
                v.getShare(), v.getPageCount());

        // follower stat
        if (v.getOwnerMid() > 0) {
            final String relApi = "https://api.bilibili.com/x/relation/stat?vmid=" + v.getOwnerMid();
            log.debug("Requesting follower stat: {}", relApi);
            try {
                final JsonObject rel = getJson(relApi, 6);
                if (rel != null && ju.getInt(rel, "code", -999) == 0) {
                    final JsonObject d = ju.getObj(rel, "data");
                    if (d != null) {
                        v.setFans(ju.getLong(d, "follower", 0L));
                        log.info("Fetched follower count={} for mid={}", v.getFans(), v.getOwnerMid());
                    } else {
                        log.warn("'data' missing in follower API for mid={}", v.getOwnerMid());
                    }
                } else {
                    log.warn("Follower API non-zero/invalid response for mid={}, url={}, message='{}'",
                            v.getOwnerMid(), relApi, (rel == null ? "null" : ju.getString(rel, "message", "")));
                }
            } catch (Exception e) {
                log.debug("Fetch fans failed: mid={}, err={}", v.getOwnerMid(), e.toString());
            }
        } else {
            log.debug("Skip follower fetch: ownerMid is 0 (owner='{}')", v.getOwnerName());
        }
        return v;
    }

    /* ===================== Render ===================== */

    private static String resolveB23(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", UA)
                    .GET()
                    .build();

            log.debug("Resolving b23 short URL: {}", url);
            HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            log.debug("b23 response status={}, headers={}", resp.statusCode(), resp.headers().map());

            // b23.tv 通常 302，并在 Location 指向 https://www.bilibili.com/video/BVxxxx
            Optional<String> loc = resp.headers().firstValue("location");
            if (loc.isPresent()) {
                log.info("b23 redirect location={}", loc.get());
            } else {
                log.warn("b23 missing Location header for {}", url);
            }

            String location = loc.get();
            Matcher mbv = BV_PATTERN.matcher(location);
            if (mbv.find()) return "BV" + mbv.group(1);

            // 有时会跳到非标准中转页，再 GET 一次
            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create(location))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", UA)
                    .GET()
                    .build();

            log.debug("Performing secondary request to {}", location);
            HttpResponse<String> resp2 = HTTP.send(req2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("Secondary response URI={}, length={}", resp2.uri(), resp2.body().length());

            Matcher mbv2 = BV_PATTERN.matcher(resp2.uri().toString());
            if (mbv2.find()) return "BV" + mbv2.group(1);

            Matcher mbv3 = BV_PATTERN.matcher(resp2.body());
            if (mbv3.find()) return "BV" + mbv3.group(1);
            return null;
        } catch (HttpTimeoutException te) {
            log.warn("Resolve b23 timeout: {}", url);
            return null;
        } catch (Exception e) {
            log.warn("Resolve b23 failed: {}", url, e);
            return null;
        }
    }

    private static JsonObject getJson(String url, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .GET()
                .build();

        log.debug("GET JSON from {}, timeout={}s", url, timeoutSec);
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() / 100 != 2) {
            log.error("HTTP {} for {}: body={}", resp.statusCode(), url,
                    resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }
        log.debug("Received {} bytes from {}", resp.body().length(), url);

        return ju.fromJsonString(resp.body(), JsonObject.class);
    }

    public static String buildViewApi(String bvid, Long aid) {
        return (bvid != null)
                ? "https://api.bilibili.com/x/web-interface/view?bvid=" + URLEncoder.encode(bvid, StandardCharsets.UTF_8)
                : "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
    }

    /**
     * pubdate: epoch seconds -> ms; missing => 0
     */
    public static long publishDateMs(JsonObject data) {
        long sec = ju.getLong(data, "pubdate", 0L);
        return (sec <= 0) ? 0L : sec * 1000L;
    }

    /**
     * pages count from `pages[].size()` or fallback `videos` field; at least 1
     */
    public static int pageCount(JsonObject data) {
        int byArray = (data != null && data.has("pages") && data.get("pages").isJsonArray())
                ? data.getAsJsonArray("pages").size() : 0;
        int byField = ju.getInt(data, "videos", 0);
        int pages = Math.max(byArray, byField);
        return Math.max(1, pages);
    }

    /* ===================== 数据获取 ===================== */

    @Override
    public String name() {
        return "bilibili";
    }

    @Override
    public String description() {
        return "查看B站视频（支持 AV/BV/短链）";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/bilibili <code|link> [-i]` # 获取 B 站视频信息，支持 AV/BV 号或 b23 短链。添加 -i 参数可获取更详细的信息。
                示例：
                `/bilibili BV1GJ411x7h7`
                `/bilibili av170001`
                `/bilibili https://b23.tv/xxxxxx -i
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal("bilibili")
                        .then(argument("id", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String raw = StringArgumentType.getString(ctx, "id");
                                    return run(ctx.getSource(), raw, false);
                                })
                                .then(literal("-i")
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "id");
                                            return run(ctx.getSource(), raw, true);
                                        })
                                )
                        )
        );
    }
}
