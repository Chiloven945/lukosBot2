package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.util.feature.IpService;

import java.io.IOException;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "ip",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class IpCommand implements IBotCommand {
    public static final IpService IS = new IpService();

    @Override
    public String name() {
        return "ip";
    }

    @Override
    public String description() {
        return "查询 IP 信息";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("查询 IP 地址信息", UsageNode.arg("ip_address"))
                .param("ip_address", "IP 地址（IPv4 / IPv6）")
                .example(
                        "ip 1.1.1.1",
                        "ip 2606:4700:4700::1111"
                )
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
                            return 1;
                        })
                        .then(argument("ip", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    try {
                                        ctx.getSource().reply(
                                                IS.getIpInfo(StringArgumentType.getString(ctx, "ip")).toString()
                                        );
                                        return 1;
                                    } catch (IOException e) {
                                        log.warn("Failed to fetch ip information.", e);
                                        ctx.getSource().reply(e.getMessage());
                                        return 0;
                                    }
                                })
                        )
        );
    }
}
