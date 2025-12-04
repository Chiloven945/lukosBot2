package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /coin command for simulating coin tosses.
 *
 * @author Chiloven945
 */
@Service
public class CoinCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(CoinCommand.class);

    private String runCoin(long times) {
        double p2 = 0.000000000002;

        // 1) C2 ~ Binomial(times, p2) ≈ N(mean, var)
        long c2 = Math.round(times * p2 + Math.sqrt(times * p2 * (1 - p2)) * nextGaussian());

        if (c2 < 0) c2 = 0;
        if (c2 > times) c2 = times;
        long remain = times - c2;

        // 2) C1 ~ Binomial(remain, 0.5)
        long c1 = Math.round(remain * 0.5 + Math.sqrt(remain * 0.5 * 0.5) * nextGaussian());

        if (c1 < 0) c1 = 0;
        if (c1 > remain) c1 = remain;
        long c0 = remain - c1;

        return """
                你抛了 %d 个硬币。
                在这些硬币中，有 %d 个是正面，%d 个是反面……
                还有 %d 个立起来了！
                """.formatted(times, c1, c0, c2);
    }

    private double nextGaussian() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double u1 = rnd.nextDouble();
        double u2 = rnd.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
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
