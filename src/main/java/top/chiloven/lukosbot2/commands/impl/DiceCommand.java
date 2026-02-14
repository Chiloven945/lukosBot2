package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.MathUtils;

import java.math.BigInteger;
import java.util.stream.IntStream;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "dice",
        havingValue = "true",
        matchIfMissing = true
)
public class DiceCommand implements IBotCommand {

    private static final MathUtils mu = MathUtils.getMathUtils();

    @Override
    public String name() {
        return "dice";
    }

    @Override
    public String description() {
        return "掷骰子，可以指定骰子数量";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("掷骰子（默认 1 个）", UsageNode.opt(UsageNode.arg("count")))
                .param("count", "骰子数量（正整数，可选，默认 1）")
                .example(
                        "dice",
                        "dice 3"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(runDice(1));
                            return 1;
                        })
                        .then(argument("count", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    long count;
                                    try {
                                        count = Long.parseLong(StringArgumentType.getString(ctx, "count"));
                                    } catch (NumberFormatException e) {
                                        ctx.getSource().reply("骰子数量必须是一个正整数。\n" + usage());
                                        return 0;
                                    }
                                    ctx.getSource().reply(runDice(count));
                                    return 1;
                                })
                        )
        );
    }

    private String runDice(long count) {
        if (count <= 0) {
            return "骰子数量必须是一个正整数。\n" + usage();
        }

        long[] faces = mu.approximateMultinomial(count,
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

        if (count == 1) {
            int face = IntStream.range(0, 6)
                    .filter(i -> faces[i] > 0)
                    .findFirst()
                    .orElse(0) + 1;

            return """
                    你掷了 1 个骰子。
                    朝上的一面是……%d！
                    """.formatted(face);
        } else {
            BigInteger sum = IntStream.range(0, faces.length)
                    .mapToObj(i -> BigInteger.valueOf(faces[i])
                            .multiply(BigInteger.valueOf(i + 1L)))
                    .reduce(BigInteger.ZERO, BigInteger::add);

            return """
                    你掷了 %d 个骰子。
                    其中，1朝上的一面有 %d 个、2有 %d 个、3有 %d 个、4有 %d 个、5有 %d 个、6有 %d 个。
                    他们合计起来是 %s！
                    """.formatted(
                    count,
                    faces[0], faces[1], faces[2], faces[3], faces[4], faces[5],
                    sum.toString()
            );
        }
    }
}
