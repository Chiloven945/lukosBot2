package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.*;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.command.CommandRegistry;
import top.chiloven.lukosbot2.core.command.CommandSource;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
public class HelpCommand implements IBotCommand {
    private final ObjectProvider<CommandRegistry> registryProvider;
    private final AppProperties appProperties;

    public HelpCommand(ObjectProvider<CommandRegistry> registryProvider, AppProperties appProperties) {
        this.registryProvider = registryProvider;
        this.appProperties = appProperties;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        // /help
                        .executes(ctx -> {
                            String p = appProperties.getPrefix();
                            StringBuilder sb = new StringBuilder("可用命令：\n");
                            registry().all().stream()
                                    .filter(IBotCommand::isVisible)
                                    .forEach(c -> sb.append(p)
                                            .append(c.name())
                                            .append(" - ")
                                            .append(c.description())
                                            .append("\n")
                                    );
                            sb.append("\n使用 `")
                                    .append(p).append(name())
                                    .append(" <command>` 查看具体命令的用法（可自动转图片）。");
                            ctx.getSource().reply(sb.toString().trim());
                            return 1;
                        })
                        // /help <command>
                        .then(argument("command", StringArgumentType.word())
                                .executes(ctx -> {
                                    String cmdName = StringArgumentType.getString(ctx, "command");
                                    return showUsage(ctx.getSource(), cmdName, null);
                                })
                                // /help <command> <mode>
                                .then(argument("mode", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String cmdName = StringArgumentType.getString(ctx, "command");
                                            String mode = StringArgumentType.getString(ctx, "mode");
                                            return showUsage(ctx.getSource(), cmdName, mode);
                                        })
                                )
                        )
        );
    }

    private int showUsage(CommandSource src, String cmdName, String modeRaw) {
        String p = appProperties.getPrefix();
        IBotCommand cmd = registry().get(cmdName);

        if (cmd == null || !cmd.isVisible()) {
            src.reply("未知的命令: %s\n使用 `%s%s` 查看可用命令列表。".formatted(cmdName, p, name()));
            return 0;
        }

        UsageNode node = cmd.usage();
        UsageTextRenderer.Options opt = UsageTextRenderer.Options.forHelp(p);

        UsageOutput.sendUsage(
                src,
                p,
                cmd.name(),
                node,
                opt,
                UsageImageUtils.ImageStyle.defaults(),
                UsageOutput.parseMode(modeRaw)
        );

        return 1;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "列出可用命令或其详细用法";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("列出所有可用命令")
                .syntax("查看命令用法（可选强制输出方式）",
                        UsageNode.arg("command"),
                        UsageNode.optOneOf(UsageNode.lit("img"), UsageNode.lit("text"))
                )
                .param("command", "命令名（不带前缀），例如：wiki / music / github")
                .note(
                        "输出方式：",
                        "- `img`：强制输出图片版用法（长文本更易读）",
                        "- `text`：强制输出文本版用法",
                        "- 不指定时自动决定：当文本过长或结构复杂时会转为图片输出"
                )
                .example(
                        "help",
                        "help wiki",
                        "help wiki img"
                )
                .build();
    }

    private CommandRegistry registry() {
        return registryProvider.getObject();
    }
}
