package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.feature.MojangApi;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * Query command for Minecraft: Java Edition players.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "player",
        havingValue = "true",
        matchIfMissing = true
)
public class PlayerCommand implements IBotCommand {
    public static final MojangApi MAPI = new MojangApi();

    @Override
    public String name() {
        return "player";
    }

    @Override
    public String description() {
        return "查询 Java 版玩家信息";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("查询玩家信息", UsageNode.oneOf(UsageNode.arg("name"), UsageNode.arg("uuid")))
                .syntax("根据用户名获取 UUID", UsageNode.arg("name"), UsageNode.lit("-u"))
                .syntax("根据 UUID 获取用户名", UsageNode.arg("uuid"), UsageNode.lit("-n"))
                .param("name", "玩家用户名（Java 版）")
                .param("uuid", "玩家 UUID（不带横线或带横线均可）")
                .option("-u", "强制按“用户名 → UUID”查询")
                .option("-n", "强制按“UUID → 用户名”查询")
                .example(
                        "player jeb_",
                        "player Notch -u"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usageText());
                            return 1;
                        })
                        // /player <name|uuid>
                        .then(argument("data", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSource src = ctx.getSource();
                                    String data = StringArgumentType.getString(ctx, "data");
                                    src.reply(MAPI.getMcPlayerInfo(data).toString());
                                    return 1;
                                })
                                // /player <data> <param>
                                .then(argument("param", StringArgumentType.word())
                                        .executes(ctx -> {
                                            CommandSource src = ctx.getSource();
                                            String data = StringArgumentType.getString(ctx, "data");
                                            String param = StringArgumentType.getString(ctx, "param");

                                            String result = switch (param) {
                                                case "-u" -> MAPI.getUuidFromName(data);
                                                case "-n" -> MAPI.getNameFromUuid(data);
                                                default -> "不正确的参数：" + param;
                                            };

                                            src.reply(result);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
