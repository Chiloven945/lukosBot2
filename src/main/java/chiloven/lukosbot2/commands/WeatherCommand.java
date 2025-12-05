package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "weather",
        havingValue = "true",
        matchIfMissing = true
)
public class WeatherCommand implements BotCommand {

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询天气";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {

    }
}
