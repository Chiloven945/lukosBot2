package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.util.JsonUtils;
import chiloven.lukosbot2.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliCommand implements BotCommand {

    private static final Logger log = LogManager.getLogger(BilibiliCommand.class);

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
     * 执行一次查询并回复
     */
    private static int run(CommandSource src, String rawInput, boolean detailed) {
        log.info("BilibiliCommand invoked by {} with input='{}', detailed={}",
                src.in().addr(), rawInput, detailed);

        String input = rawInput.trim();
        log.debug("Raw input after trim: '{}'", input);

        // 1) 解析 b23 短链
        if (input.startsWith("http")) {
            log.debug("Attempting to resolve b23 short link: {}", input);
            String bv = resolveB23(input);
            if (bv == null) {
                log.warn("Failed to resolve b23 link: {}", input);
                src.reply("无法解析短链接：" + input);
                return 0;
            }
            log.info("Resolved b23 short link to BV={}", bv);
            input = bv;
        }

        // 2) 识别 AV / BV
        log.debug("Matching input to AV/BV pattern...");
        String bvid = null;
        Long aid = null;

        Matcher mbv = BV_PATTERN.matcher(input);
        if (mbv.matches() || mbv.find()) {
            bvid = "BV" + mbv.group(1);
        } else {
            Matcher mav = AV_PATTERN.matcher(input);
            if (mav.matches() || mav.find()) {
                try {
                    aid = Long.parseLong(mav.group(1));
                } catch (NumberFormatException ignore) {
                }
            }
        }

        if (bvid != null) log.info("Detected BV id={}", bvid);

        if (aid != null) log.info("Detected AV id={}", aid);

        if (bvid == null && aid == null) {
            src.reply("无效的视频编号（仅支持 AV/BV 或 b23 短链）");
            return 0;
        }

        try {
            // 3) 调 B站 API 获取视频信息
            log.info("Fetching Bilibili video info for bvid={}, aid={}", bvid, aid);
            VideoInfo vi = fetchVideoInfo(bvid, aid);
            if (vi == null) {
                log.warn("Video not found for bvid={}, aid={}", bvid, aid);
                src.reply("未找到该视频");
                return 0;
            }
            log.info("Fetched video: title='{}', owner='{}', view={}", vi.title, vi.ownerName, vi.view);

            // 4) 组装消息
            log.debug("Rendering message, detailed={}", detailed);
            String canonical = "https://www.bilibili.com/video/" + vi.bvid;
            String text = detailed ? renderDetailed(vi, canonical) : renderSimple(vi, canonical);

            if (vi.cover != null && !vi.cover.isBlank()) {
                src.reply(text + "\n" + vi.cover);
            } else {
                src.reply(text);
            }

            log.info("BilibiliCommand completed successfully for input='{}'", rawInput);
            return 1;
        } catch (Exception e) {
            log.error("Exception executing BilibiliCommand for input='{}'", input, e);
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
                v.title,
                StringUtils.nvl(v.tname),
                StringUtils.nvl(v.ownerName),
                formatTime(v.pubDateMs)
        );
    }

    private static String renderDetailed(VideoInfo v, String link) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(link).append("\n");
        sb.append("标题：").append(v.title);
        if (v.pageCount > 1) sb.append("（").append(v.pageCount).append("P）");
        sb.append(" | 类型：").append(StringUtils.nvl(v.tname)).append("\n");

        sb.append("UP主：").append(StringUtils.nvl(v.ownerName)).append(" | 粉丝：").append(StringUtils.formatNum(v.fans)).append("\n");

        if (v.desc != null && !v.desc.isBlank()) {
            sb.append("简介：").append(StringUtils.truncate(v.desc, 160)).append("\n");
        }

        sb.append("观看：").append(StringUtils.formatNum(v.view))
                .append(" | 弹幕：").append(StringUtils.formatNum(v.danmaku))
                .append(" | 评论：").append(StringUtils.formatNum(v.reply)).append("\n");

        sb.append("喜欢：").append(StringUtils.formatNum(v.like))
                .append(" | 投币：").append(StringUtils.formatNum(v.coin))
                .append(" | 收藏：").append(StringUtils.formatNum(v.favorite))
                .append(" | 分享：").append(StringUtils.formatNum(v.share)).append("\n");

        sb.append("日期：").append(formatTime(v.pubDateMs));
        return sb.toString();
    }

    private static VideoInfo fetchVideoInfo(String bvid, Long aid) throws Exception {
        String api = (bvid != null)
                ? "https://api.bilibili.com/x/web-interface/view?bvid=" + url(bvid)
                : "https://api.bilibili.com/x/web-interface/view?aid=" + aid;

        // ===== 请求前 =====
        log.debug("Requesting Bilibili API: {}", api);

        JsonObject root = getJson(api, 8);

        // ===== 空响应 =====
        if (root == null) {
            log.error("Null JSON response from {}", api);
            return null;
        }

        // ===== code 字段检查（官方返回约定：0 为成功）=====
        int code = root.has("code") ? root.get("code").getAsInt() : -999;
        String message = root.has("message") && !root.get("message").isJsonNull()
                ? root.get("message").getAsString() : "";
        log.debug("API response code={}, message='{}' for {}", code, message, api);

        if (code != 0) {
            log.warn("Non-zero API code={} (message='{}') for {}", code, message, api);
            return null;
        }

        JsonObject data = root.getAsJsonObject("data");
        if (data == null) {
            log.warn("API response has no 'data' object for {}", api);
            return null;
        }

        // ===== 解析视频主体 =====
        VideoInfo v = new VideoInfo();

        v.bvid = data.has("bvid") && !data.get("bvid").isJsonNull() ? data.get("bvid").getAsString() : null;
        if (v.bvid == null && bvid != null) v.bvid = bvid;

        v.title = data.has("title") ? data.get("title").getAsString() : "";
        v.tname = data.has("tname") ? data.get("tname").getAsString() : "";
        v.desc  = data.has("desc")  ? data.get("desc").getAsString()  : "";
        v.cover = data.has("pic")   ? data.get("pic").getAsString()   : "";

        v.pubDateMs = data.has("pubdate") ? data.get("pubdate").getAsLong() * 1000L : 0L;

        // 所有者
        JsonObject owner = data.has("owner") && data.get("owner").isJsonObject()
                ? data.getAsJsonObject("owner") : new JsonObject();
        v.ownerName = owner.has("name") ? owner.get("name").getAsString() : "";
        v.ownerMid  = owner.has("mid")  ? owner.get("mid").getAsLong()    : 0L;

        // 统计
        JsonObject stat = data.has("stat") && data.get("stat").isJsonObject()
                ? data.getAsJsonObject("stat") : new JsonObject();
        v.view     = stat.has("view")     ? stat.get("view").getAsLong()     : 0L;
        v.danmaku  = stat.has("danmaku")  ? stat.get("danmaku").getAsLong()  : 0L;
        v.reply    = stat.has("reply")    ? stat.get("reply").getAsLong()    : 0L;
        v.favorite = stat.has("favorite") ? stat.get("favorite").getAsLong() : 0L;
        v.coin     = stat.has("coin")     ? stat.get("coin").getAsLong()     : 0L;
        v.share    = stat.has("share")    ? stat.get("share").getAsLong()    : 0L;
        v.like     = stat.has("like")     ? stat.get("like").getAsLong()     : 0L;

        // 分 P
        int pages = 0;
        JsonArray arr = data.has("pages") && data.get("pages").isJsonArray()
                ? data.getAsJsonArray("pages") : null;
        if (arr != null) pages = arr.size();
        if (pages <= 0 && data.has("videos")) pages = data.get("videos").getAsInt();
        v.pageCount = Math.max(1, pages);
        log.debug("Parsed pages: pageCount={}, hasPagesArray={}, videosField={}",
                v.pageCount, (arr != null), data.has("videos"));

        // 解析完成的摘要（标题/分区/作者/基础播放数据）
        log.info("Parsed video: bvid={}, aid={}, title='{}', tname='{}', owner='{}'(mid={}), "
                        + "view={}, danmaku={}, reply={}, like={}, coin={}, fav={}, share={}, pageCount={}",
                v.bvid, aid, v.title, v.tname, v.ownerName, v.ownerMid,
                v.view, v.danmaku, v.reply, v.like, v.coin, v.favorite, v.share, v.pageCount);

        // ===== 粉丝数（额外接口）=====
        if (v.ownerMid > 0) {
            String relApi = "https://api.bilibili.com/x/relation/stat?vmid=" + v.ownerMid;
            log.debug("Requesting follower stat: {}", relApi);
            try {
                JsonObject rel = getJson(relApi, 6);
                if (rel == null) {
                    log.warn("Follower API returned null for mid={}, url={}", v.ownerMid, relApi);
                } else {
                    int relCode = rel.has("code") ? rel.get("code").getAsInt() : -999;
                    if (relCode == 0) {
                        JsonObject d = rel.getAsJsonObject("data");
                        if (d != null && d.has("follower")) {
                            v.fans = d.get("follower").getAsLong();
                            log.info("Fetched follower count={} for mid={}", v.fans, v.ownerMid);
                        } else {
                            log.warn("'data.follower' missing in follower API for mid={}", v.ownerMid);
                        }
                    } else {
                        String relMsg = rel.has("message") && !rel.get("message").isJsonNull()
                                ? rel.get("message").getAsString() : "";
                        log.warn("Follower API non-zero code={} (message='{}') for mid={}, url={}",
                                relCode, relMsg, v.ownerMid, relApi);
                    }
                }
            } catch (Exception e) {
                log.debug("Fetch fans failed: mid={}, err={}", v.ownerMid, e.toString());
            }
        } else {
            log.debug("Skip follower fetch: ownerMid is 0 (owner='{}')", v.ownerName);
        }

        return v;
    }

    /* ===================== 渲染 ===================== */

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

        return JsonUtils.fromJsonString(resp.body(), JsonObject.class);
    }

    /* ===================== 数据获取 ===================== */

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /* ===================== 工具 ===================== */

    private static String formatTime(long millis) {
        if (millis <= 0) return "-";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return f.format(new Date(millis));
    }

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
        return "/bilibili <AV/BV|b23短链> [-i]";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal("bilibili")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("id", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String raw = StringArgumentType.getString(ctx, "id");
                                    return run(ctx.getSource(), raw, false);
                                })
                                .then(LiteralArgumentBuilder.<CommandSource>literal("-i")
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "id");
                                            return run(ctx.getSource(), raw, true);
                                        })
                                )
                        )
        );
    }

    /* ===================== 数据模型 ===================== */

    private static final class VideoInfo {
        String bvid;
        String title;
        String tname;
        String desc;
        String cover;
        long pubDateMs;

        String ownerName;
        long ownerMid;
        long fans;

        long view, danmaku, reply, favorite, coin, share, like;
        int pageCount = 1;
    }
}
