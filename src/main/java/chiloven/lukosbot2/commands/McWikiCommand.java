package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.WebToMarkdown;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class McWikiCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(McWikiCommand.class);

    // 与 WikiCommand 一致的默认语言策略
    private static final String DEFAULT_LANG = "zh";

    private static boolean isNotMcWiki(String url) {
        try {
            var u = new URI(url).toURL();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            // 允许 zh.minecraft.wiki、en.minecraft.wiki 等
            return !host.endsWith(".minecraft.wiki") && !host.equals("minecraft.wiki");
        } catch (Exception e) {
            return true;
        }
    }

    private static String normalizeMcWiki(String linkOrTitle) {
        String s = linkOrTitle.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        return titleToMcWikiUrl(s);
    }

    /// Support optional language prefixes, like `en:Zombie` and `zh:僵尸`.
    ///
    /// Minecraft Wiki's path is `/w/Title`.
    ///
    /// @param input the title to transform to the url.
    /// @return the transformed url.
    private static String titleToMcWikiUrl(String input) {
        if (input == null) return null;
        String s = input.trim();

        String lang = DEFAULT_LANG;
        int idx = s.indexOf(':');
        if (idx > 0 && idx <= 6 && s.substring(0, idx).matches("[A-Za-z-]{2,6}")) {
            lang = s.substring(0, idx);
            s = s.substring(idx + 1).trim();
        }

        String encoded = URLEncoder.encode(s.replace(' ', '_'), StandardCharsets.UTF_8);
        return "https://" + lang + ".minecraft.wiki/w/" + encoded;
    }

    /// Fetch the h1 title and the lead
    ///
    /// @param url the url to fetch
    /// @return [TitleLead] with the title and lead
    /// @throws Exception if the connection failed
    private static TitleLead fetchTitleAndLead(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; LukosBot/1.0)")
                .timeout(15000);

        Document doc = conn.get();
        String title = textOrEmpty(doc.selectFirst("h1#firstHeading"));

        // MediaWiki 正文容器通常位于 #mw-content-text .mw-parser-output
        Element container = doc.selectFirst("#mw-content-text .mw-parser-output");
        if (container == null) {
            // 后备
            container = doc.selectFirst("#content");
        }
        if (container == null) {
            return new TitleLead(title, "");
        }

        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (Element p : container.select("> p")) {
            String t = p.text().trim();
            if (t.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(t);
            added++;
            if (added >= 2 || sb.length() >= 500) break; // 控制简介长度
        }
        return new TitleLead(title, sb.toString().trim());
    }

    // ===== 具体执行 =====

    private static String textOrEmpty(Element e) {
        return e == null ? "" : e.text().trim();
    }

    @Override
    public String name() {
        return "mcwiki";
    }

    // ===== URL / 站点判断 & 解析 =====

    @Override
    public String description() {
        return "Minecraft Wiki 工具：返回标题+简介，或整页 Markdown";
    }

    @Override
    public String usage() {
        return """
                用法：
                /mcwiki <minecraftwiki_link|article>     # 返回“标题\\n简介”
                /mcwiki md <minecraftwiki_link|article>  # 抓取整页并转为 Markdown 文件
                示例：
                /mcwiki 僵尸猪灵
                /mcwiki md https://zh.minecraft.wiki/w/僵尸猪灵
                /mcwiki en:Zombie_Piglin
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        // /mcwiki <link>
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("link", greedyString())
                                .executes(ctx -> {
                                    String link = StringArgumentType.getString(ctx, "link").trim();
                                    runSummary(ctx.getSource(), link);
                                    return 1;
                                })
                        )
                        // /mcwiki md <link>
                        .then(LiteralArgumentBuilder.<CommandSource>literal("md")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runMarkdown(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        // /mcwiki ss <link>
                        .then(LiteralArgumentBuilder.<CommandSource>literal("ss")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runScreenshot(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        // 无参时给出用法
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
        );
    }

    // ===== 抓取简介段落 =====

    private void runSummary(CommandSource src, String linkOrTitle) {
        try {
            String url = normalizeMcWiki(linkOrTitle);
            if (isNotMcWiki(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki https://zh.minecraft.wiki/w/下界合金");
                return;
            }

            // 抓取标题 + 简介段
            var summary = fetchTitleAndLead(url);
            if (summary.title().isBlank()) {
                src.reply("未抓到有效内容。请检查条目是否存在。");
                return;
            }

            String text = summary.title() + "\n" + summary.lead();
            MessageOut out = MessageOut.text(src.in().addr(), text);
            src.reply(out);
        } catch (Exception e) {
            log.warn("McWiki summary failed: {}", linkOrTitle, e);
            src.reply("获取失败：" + e.getMessage());
        }
    }

    private void runMarkdown(CommandSource src, String linkOrTitle) {
        try {
            String url = normalizeMcWiki(linkOrTitle);
            if (isNotMcWiki(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki md https://zh.minecraft.wiki/w/下界合金");
                return;
            }

            // 直接复用 WebToMarkdown 的通用转换（见下文新增方法）
            var md = WebToMarkdown.fetchAndConvertWithSelectors(
                    url,
                    "h1#firstHeading",                // 标题选择器（与 MediaWiki 一致）
                    "#content, #mw-content-text",     // 内容区主容器（两个择一）
                    "mcwiki"                          // 默认文件名前缀
            );

            MessageOut out = MessageOut.text(src.in().addr(), "已转换为 Markdown")
                    .with(Attachment.fileBytes(md.filename(), md.bytes(), md.mime()));
            src.reply(out);
        } catch (Exception e) {
            log.warn("McWiki md failed: {}", linkOrTitle, e);
            src.reply("转换失败：" + e.getMessage());
        }
    }

    private void runScreenshot(CommandSource src, String linkOrTitle) {
        try {
            String url = normalizeMcWiki(linkOrTitle);
            if (isNotMcWiki(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki ss https://zh.minecraft.wiki/w/下界合金");
                return;
            }

            // 获取 ContentData 并通过 Attachment.imageBytes 返回
            var cd = chiloven.lukosbot2.util.WebScreenshot.screenshotMcWiki(url);

            MessageOut out = MessageOut.text(src.in().addr(), "截图如下：")
                    .with(Attachment.imageBytes(cd.filename(), cd.bytes(), cd.mime()));
            src.reply(out);

        } catch (Exception e) {
            log.warn("McWiki screenshot failed: {}", linkOrTitle, e);
            src.reply("截图失败：" + e.getMessage());
        }
    }

    private record TitleLead(String title, String lead) {
    }
}
