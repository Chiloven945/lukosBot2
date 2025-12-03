package chiloven.lukosbot2.commands.wikis;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.feature.WebScreenshot;
import chiloven.lukosbot2.util.feature.WebToMarkdown;
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
import org.springframework.stereotype.Service;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * The /mcwiki command for Minecraft Wiki title+lead, markdown conversion, and screenshots.
 *
 * @author Chiloven945
 */
@Service
public class McWikiCommand implements WikiishCommand {
    private static final Logger log = LogManager.getLogger(McWikiCommand.class);

    /**
     * Fetch title and lead paragraph(s) from a Minecraft Wiki article.
     *
     * @param url The full URL of the Minecraft Wiki article.
     * @return {@link TitleLead} object containing the title and lead text.
     * @throws Exception if fetching or parsing fails.
     */
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

    /**
     * Get trimmed text of an element, or empty string if null.
     *
     * @param e The Jsoup Element.
     * @return Trimmed text or empty string.
     */
    private static String textOrEmpty(Element e) {
        return e == null ? "" : e.text().trim();
    }

    @Override
    public String defaultLang() {
        return "zh";
    }

    @Override
    public String pathPrefix() {
        return "/w/";
    }

    @Override
    public String domainRoot() {
        return "minecraft.wiki";
    }

    @Override
    public String name() {
        return "mcwiki";
    }

    @Override
    public String description() {
        return "Minecraft Wiki 工具：返回标题+简介，或整页 Markdown";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/mcwiki <minecraftwiki_link|article>`    # 返回“标题\\n简介”
                `/mcwiki md <minecraftwiki_link|article>` # 抓取整页并转为 Markdown 文件
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

    /**
     * Run the summary subcommand: fetch title and lead paragraph(s).
     *
     * @param src         The command source.
     * @param linkOrTitle The Minecraft Wiki link or article title.
     */
    private void runSummary(CommandSource src, String linkOrTitle) {
        try {
            String url = normalize(linkOrTitle);
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki https://zh.minecraft.wiki/w/下界合金");
                return;
            }

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

    /**
     * Run the markdown subcommand: fetch and convert to Markdown.
     *
     * @param src         The command source.
     * @param linkOrTitle The Minecraft Wiki link or article title.
     */
    private void runMarkdown(CommandSource src, String linkOrTitle) {
        try {
            String url = normalize(linkOrTitle);
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki md https://zh.minecraft.wiki/w/下界合金");
                return;
            }

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

    /**
     * Run the screenshot subcommand: capture a screenshot of the article.
     *
     * @param src         The command source.
     * @param linkOrTitle The Minecraft Wiki link or article title.
     */
    private void runScreenshot(CommandSource src, String linkOrTitle) {
        try {
            String url = normalize(linkOrTitle);
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki ss https://zh.minecraft.wiki/w/下界合金");
                return;
            }

            var cd = WebScreenshot.screenshotMcWiki(url);

            MessageOut out = MessageOut.text(src.in().addr(), "截图如下：")
                    .with(Attachment.imageBytes(cd.filename(), cd.bytes(), cd.mime()));
            src.reply(out);

        } catch (Exception e) {
            log.warn("McWiki screenshot failed: {}", linkOrTitle, e);
            src.reply("截图失败：" + e.getMessage());
        }
    }

    /**
     * Title and lead paragraph(s) record.
     *
     * @param title the article title
     * @param lead  the lead paragraph(s)
     */
    private record TitleLead(String title, String lead) {
    }
}
