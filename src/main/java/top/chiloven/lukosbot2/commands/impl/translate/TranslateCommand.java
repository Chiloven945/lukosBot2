package top.chiloven.lukosbot2.commands.impl.translate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.config.CommandConfigProp;
import top.chiloven.lukosbot2.config.CommandConfigProp.Translate;
import top.chiloven.lukosbot2.core.command.CommandSource;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "translate",
        havingValue = "true",
        matchIfMissing = true
)
public class TranslateCommand implements IBotCommand {
    public final TranslationService ts;
    private final Translate translate;

    public TranslateCommand(CommandConfigProp ccp) {
        this.translate = ccp.getTranslate();
        this.ts = new TranslationService(translate);
    }

    @Override
    public String name() {
        return "translate";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("翻译文本（默认自动检测语言）", UsageNode.arg("text"))
                .syntax("翻译文本（可选指定源/目标语言）",
                        UsageNode.opt(UsageNode.group(UsageNode.lit("-f"), UsageNode.arg("from_lang"))),
                        UsageNode.opt(UsageNode.group(UsageNode.lit("-t"), UsageNode.arg("to_lang"))),
                        UsageNode.arg("text")
                )
                .param("text", "要翻译的文本（支持空格）")
                .option("-f", "指定源语言（可选；不指定则自动检测）")
                .option("-t", "指定目标语言（可选；不指定则使用默认目标语言）")
                .example(
                        "/translate the quick brown fox over the lazy dog",
                        "/translate -f en -t zh-Hans the quick brown fox over the lazy dog",
                        "/translate -t ja Do not go gentle into that good night"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usageText());
                            return 0;
                        })

                        .then(argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    try {
                                        CommandSource src = ctx.getSource();
                                        String args = StringArgumentType.getString(ctx, "args");

                                        var results = Pattern.compile("-f\\s+(?<from>\\S+)|-t\\s+(?<to>\\S+)")
                                                .matcher(args)
                                                .results()
                                                .toList();

                                        src.reply(run(
                                                results.stream()
                                                        .map(r -> r.group("from"))
                                                        .findFirst()
                                                        .orElse("auto"),
                                                results.stream()
                                                        .map(r1 -> r1.group("to"))
                                                        .findFirst()
                                                        .orElse(translate.getDefaultLang()),
                                                Arrays.stream(args.split("\\s+"))
                                                        .filter(s -> !s.equals("-f") && !s.equals("-t"))
                                                        .filter(s -> results.stream().noneMatch(r2 ->
                                                                s.equals(r2.group("from")) || s.equals(r2.group("to"))
                                                        ))
                                                        .collect(Collectors.joining(" "))
                                                        .trim()));
                                        return 0;
                                    } catch (Exception e) {
                                        ctx.getSource().reply("不正确的格式。");
                                        return 1;
                                    }
                                })
                        )
        );
    }

    private String run(String from, String to, String text) {
        return ts.translate(from, to, text);
    }
}
