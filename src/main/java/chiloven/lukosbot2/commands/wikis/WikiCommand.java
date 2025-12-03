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
import org.springframework.stereotype.Service;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * The /wiki command for Wikipedia screenshots and markdown conversion.
 *
 * @author Chiloven945
 */
@Service
public class WikiCommand implements WikiishCommand {
    private static final Logger log = LogManager.getLogger(WikiCommand.class);

    @Override
    public String defaultLang() {
        return "zh";
    }

    @Override
    public String pathPrefix() {
        return "/wiki/";
    }

    @Override
    public String domainRoot() {
        return "wikipedia.org";
    }

    @Override
    public String name() {
        return "wiki";
    }

    @Override
    public String description() {
        return "维基百科工具，支持截图和转 Markdown";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/wiki <wikipedia_link|article>`    # 生成页面截图
                `/wiki md <wikipedia_link|article>` # 抓取并转为 Markdown 文件
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

    private void runScreenshot(CommandSource src, String link) {
        try {
            String url = normalize(link);
            if (isNot(url)) {
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
            if (isNot(url)) {
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
