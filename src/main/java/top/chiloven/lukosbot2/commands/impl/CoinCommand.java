package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.MathUtils;

import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

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

    @Override
    public String name() {
        return "coin";
    }

    @Override
    public String description() {
        return "抛硬币";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("抛 count 个硬币", UsageNode.opt(UsageNode.arg("count")))
                .param("count", "硬币数量（正整数）")
                .example("coin 10")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(literal(name())
                .executes(ctx -> {
                    ctx.getSource().reply(runCoin(1L));
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

    private String runCoin(long times) {
        if (times <= 0) {
            return "硬币数量必须是一个正整数。\n" + usage();
        }

        try {
            long[] r = MathUtils.approximateMultinomial(times, 0.499999999999d, 0.499999999999d, 0.000000000002d);

            return (times == 1)
                    ? "你抛了 1 个硬币。\n" + (r[0] == 1 ? "是正面。" : r[1] == 1 ? "是反面。" : "它立起来了！")
                    : """
                    你抛了 %d 个硬币。
                    在这些硬币中，有 %d 个是正面，%d 个是反面……
                    还有 %d 个立起来了！
                    """.formatted(times, r[0], r[1], r[2]);
        } catch (IllegalArgumentException e) {
            return "出现错误：" + e.getMessage();
        }
    }

}
