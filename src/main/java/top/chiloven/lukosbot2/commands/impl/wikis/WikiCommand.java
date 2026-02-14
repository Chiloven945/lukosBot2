package top.chiloven.lukosbot2.commands.impl.wikis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
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
 * The /wiki command for Wikipedia screenshots and markdown conversion.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "wiki",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class WikiCommand implements IWikiishCommand {

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
    public UsageNode usage() {
        UsageNode.Item target = UsageNode.oneOf(UsageNode.arg("link"), UsageNode.arg("article"));
        return UsageNode.root(name())
                .description(description())
                .syntax("生成页面截图", target)
                .subcommand("md", "抓取整页并转为 Markdown 文件", b -> b
                        .syntax("抓取整页并转为 Markdown 文件", target)
                )
                .param("link", "维基百科链接")
                .param("article", "词条名（可用 `en:` 前缀指定语言）")
                .example(
                        "wiki Java",
                        "wiki en:Albert_Einstein",
                        "wiki md https://zh.wikipedia.org/wiki/Java"
                )
                .note("词条名支持 `en:` 前缀指定语言，例如：`en:Albert_Einstein`。")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        // /wiki <link>
                        .then(argument("link", greedyString())
                                .executes(ctx -> {
                                    String link = StringArgumentType.getString(ctx, "link").trim();
                                    runScreenshot(ctx.getSource(), link);
                                    return 1;
                                })
                        )
                        // /wiki md <link>
                        .then(literal("md")
                                .then(argument("link", greedyString())
                                        .executes(ctx -> {
                                            String link = StringArgumentType.getString(ctx, "link").trim();
                                            runMarkdown(ctx.getSource(), link);
                                            return 1;
                                        })
                                )
                        )
                        // No args
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
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
