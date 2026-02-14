package top.chiloven.lukosbot2.commands.impl.wikis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.model.Attachment;
import top.chiloven.lukosbot2.model.MessageOut;
import top.chiloven.lukosbot2.util.feature.WebScreenshot;
import top.chiloven.lukosbot2.util.feature.WebToMarkdown;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /mcwiki command for Minecraft Wiki title+lead, markdown conversion, and screenshots.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "mcwiki",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class McWikiCommand implements IWikiishCommand {

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

        Element container = doc.selectFirst("#mw-content-text .mw-parser-output");
        if (container == null) {
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
    public UsageNode usage() {
        UsageNode.Item target = UsageNode.oneOf(UsageNode.arg("link"), UsageNode.arg("article"));
        return UsageNode.root(name())
                .description(description())
                .syntax("生成页面截图", target)
                .subcommand("md", "抓取整页并转为 Markdown 文件", b -> b
                        .syntax("抓取整页并转为 Markdown 文件", target)
                )
                .param("link", "Minecraft Wiki 链接")
                .param("article", "词条名（可用 `en:` 前缀指定语言）")
                .example(
                        "mcwiki 僵尸猪灵",
                        "mcwiki en:Zombie_Piglin",
                        "mcwiki md https://zh.minecraft.wiki/w/僵尸猪灵"
                )
                .note("词条名支持 `en:` 前缀指定语言，例如：`en:Zombie_Piglin`。")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        // /mcwiki <link>
                        .then(argument("link", greedyString())
                                .executes(ctx -> {
                                    String link = StringArgumentType.getString(ctx, "link").trim();
                                    runSummary(ctx.getSource(), link);
                                    return 1;
                                })
                        )
                        // /mcwiki md <link>
                        .then(literal("md")
                                .then(argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runMarkdown(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        // /mcwiki ss <link>
                        .then(literal("ss")
                                .then(argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runScreenshot(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        .executes(ctx -> {
                            ctx.getSource().reply(usageText());
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
