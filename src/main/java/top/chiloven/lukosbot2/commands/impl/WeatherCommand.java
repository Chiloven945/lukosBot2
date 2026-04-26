package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.core.command.CommandSource;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "weather",
        havingValue = "true",
        matchIfMissing = true
)
public class WeatherCommand implements IBotCommand {

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询天气（暂未开放）";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("该命令暂未开放")
                .note("天气查询功能尚未接入，暂时无法使用。")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }

}
