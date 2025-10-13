package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.WebScreenshot;
import chiloven.lukosbot2.util.WebToMarkdown;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class WikiCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(WikiCommand.class);

    // TODO: change to the language in the application.yml
    private static final String DEFAULT_LANG = "zh";

    /**
     * Return a correct URL of Wikipedia.
     * This allows user to use both URL and the article name.
     *
     * @param linkOrTitle the link or article title to fetch
     * @return normalized URL
     */
    private static String normalize(String linkOrTitle) {
        String s = linkOrTitle.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        return titleToWikipediaUrl(s);
    }

    private static String titleToWikipediaUrl(String input) {
        if (input == null) return null;
        String s = input.trim();

        String lang = DEFAULT_LANG;
        int idx = s.indexOf(':');
        if (idx > 0 && idx <= 6 && s.substring(0, idx).matches("[A-Za-z-]{2,6}")) {
            lang = s.substring(0, idx);
            s = s.substring(idx + 1).trim();
        }

        String title = s.replace(' ', '_');
        try {
            String enc = java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);
            return "https://" + lang + ".wikipedia.org/wiki/" + enc;
        } catch (Exception e) {
            return "https://" + lang + ".wikipedia.org/wiki/" + title;
        }
    }

    /**
     * Check if the URL belongs to Wikipedia.
     *
     * @param url the URL to check
     * @return true if it's a Wikipedia URL, false otherwise
     */
    public static boolean isNotWikipedia(String url) {
        try {
            var u = new URI(url).toURL();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            return !host.endsWith(".wikipedia.org") && !host.equals("wikipedia.org");
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String name() {
        return "wiki";
    }

    @Override
    public String description() {
        return "维基百科工具，支持截图和转 Markdown";
    }

    // ====== 执行实现 ======

    @Override
    public String usage() {
        return """
                用法：
                /wiki <wikipedia_link|article>          # 生成页面截图
                /wiki md <wikipedia_link|article>       # 抓取并转为 Markdown 文件
                示例：
                /wiki Java
                /wiki md https://zh.wikipedia.org/wiki/Java
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        // /wiki <link>
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("link", greedyString())
                                .executes(ctx -> {
                                    String link = StringArgumentType.getString(ctx, "link").trim();
                                    runScreenshot(ctx.getSource(), link);
                                    return 1;
                                })
                        )
                        // /wiki md <link>
                        .then(LiteralArgumentBuilder.<CommandSource>literal("md")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runMarkdown(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        // No args
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
        );
    }

    // ====== Tools ======

    private void runScreenshot(CommandSource src, String link) {
        try {
            String url = normalize(link);
            if (isNotWikipedia(url)) {
                src.reply("仅支持 Wikipedia 链接。示例：/wiki https://en.wikipedia.org/wiki/Java");
            }
            var img = WebScreenshot.screenshotWikipedia(url);
            MessageOut out = MessageOut.text(src.in().addr(), null)
                    .with(Attachment.imageBytes(img.filename(), img.bytes(), img.mime()));
            src.reply(out);
        } catch (Exception e) {
            log.warn("Wiki screenshot failed: {}", link, e);
            src.reply("截图失败：" + e.getMessage());
        }
    }


    private void runMarkdown(CommandSource src, String link) {
        try {
            String url = normalize(link);
            if (isNotWikipedia(url)) {
                src.reply("仅支持 Wikipedia 链接。示例：/wiki md https://zh.wikipedia.org/wiki/Java");
            }
            var md = WebToMarkdown.fetchWikipediaMarkdown(url);
            MessageOut out = MessageOut.text(src.in().addr(), "已转换为 Markdown")
                    .with(Attachment.fileBytes(md.filename(), md.bytes(), md.mime()));
            src.reply(out);
        } catch (Exception e) {
            log.warn("Wiki md failed: {}", link, e);
            src.reply("转换失败：" + e.getMessage());
        }
    }

}
