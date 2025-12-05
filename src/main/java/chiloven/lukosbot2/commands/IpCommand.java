package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import chiloven.lukosbot2.util.feature.IpService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.switch",
        name = "ip",
        havingValue = "true",
        matchIfMissing = true
)
public class IpCommand implements BotCommand {
    public static final IpService IS = new IpService();
    private static final Logger log = LogManager.getLogger(IpCommand.class);

    @Override
    public String name() {
        return "ip";
    }

    @Override
    public String description() {
        return "查询 IP 信息";
    }

    @Override
    public String usage() {
        return """
                用法：
                /ip <ip_address> # 查询 IP 地址，支持 v4 和 v6
                示例：
                /ip 1.1.1.1
                /ip 2606:4700:4700::1111
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
