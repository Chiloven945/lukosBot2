package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.util.feature.MojangApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.stereotype.Service;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * Query command for Minecraft: Java Edition players.
 *
 * @author Chiloven945
 */
@Service
public class PlayerCommand implements BotCommand {
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
    public String usage() {
        return """
                用法：
                /player <name|uuid> # 查询玩家信息
                /player <name> -u # 根据用户名获取 UUID
                /player <uuid> -n # 根据 UUID 获取用户名
                示例：
                /player jeb_
                /player Notch -u
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
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
