package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.util.MathUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /coin command for simulating coin tosses.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.switch",
        name = "coin",
        havingValue = "true",
        matchIfMissing = true
)
public class CoinCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(CoinCommand.class);
    private static final MathUtils MU = new MathUtils();

    private String runCoin(long times) {
        try {
            long[] r = MU.approximateMultinomial(times, 0.499999999999d, 0.499999999999d, 0.000000000002d);

            return """
                    你抛了 %d 个硬币。
                    在这些硬币中，有 %d 个是正面，%d 个是反面……
                    还有 %d 个立起来了！
                    """.formatted(times, r[0], r[1], r[2]);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
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
                                ctx.getSource().reply("请输入一个有效的整数作为抛硬币的数量。");
                                log.warn("Received an invalid count, expected to be a long.", e);
                                return 0;
                            }
                        })
                )
        );
    }
}
