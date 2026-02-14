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
        return "查询天气";
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("（该命令尚未实现）")
                .note("提示：此命令目前没有注册任何参数解析逻辑。")
                .build();
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }
}
