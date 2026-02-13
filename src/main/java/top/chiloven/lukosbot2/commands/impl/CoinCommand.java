package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.MathUtils;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /coin command for simulating coin tosses.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "coin",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class CoinCommand implements IBotCommand {
    private static final MathUtils mu = MathUtils.getMathUtils();

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(literal(name())
                .executes(ctx -> {
                    ctx.getSource().reply(usage());
                    return 1;
                })
                .then(argument("count", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            try {
                                ctx.getSource().reply(
                                        runCoin(Long.parseLong(StringArgumentType.getString(ctx, "count")))
                                );
                                return 1;
                            } catch (NumberFormatException e) {
                                ctx.getSource().reply("请输入一个有效的整数作为抛硬币的数量。\n" + usage());
                                log.warn("Received an invalid count, expected to be a long.", e);
                                return 0;
                            }
                        })
                )
        );
    }

    @Override
    public String name() {
        return "coin";
    }

    @Override
    public String description() {
        return "抛硬币";
    }

    @Override
    public String usage() {
        return """
                用法：
                /coin <count> # 抛 count 个硬币
                示例：
                /coin 10
                """;
    }

    private String runCoin(long times) {
        if (times <= 0) {
            return "硬币数量必须是一个正整数。\n" + usage();
        }

        try {
            long[] r = mu.approximateMultinomial(times, 0.499999999999d, 0.499999999999d, 0.000000000002d);

            return """
                    你抛了 %d 个硬币。
                    在这些硬币中，有 %d 个是正面，%d 个是反面……
                    还有 %d 个立起来了！
                    """.formatted(times, r[0], r[1], r[2]);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
