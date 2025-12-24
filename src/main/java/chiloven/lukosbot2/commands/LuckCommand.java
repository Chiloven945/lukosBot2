package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.command.CommandSource;
import chiloven.lukosbot2.util.MathUtils;
import com.mojang.brigadier.CommandDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import static chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;

@Log4j2
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "luck",
        havingValue = "true",
        matchIfMissing = true
)
public class LuckCommand implements BotCommand {
    private static final MathUtils mu = MathUtils.getMathUtils();

    @Override
    public String name() {
        return "luck";
    }

    @Override
    public String description() {
        return "获取当日的幸运值。";
    }

    @Override
    public String usage() {
        return """
                用法：
                /luck # 获取当日的幸运值。
                示例：
                /luck
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .executes(ctx -> {
                            try {
                                int luck = mu.stableRandom(
                                        0, 100,
                                        ctx.getSource().userId(),
                                        LocalDate.now()
                                );

                                ctx.getSource().reply("""
                                        你今天的幸运值是……%d！
                                        """.formatted(luck));

                                return 1;
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().reply("出现未知错误，请联系开发者");
                                log.error(e.getMessage(), e);
                                return 0;
                            }
                        })
        );
    }
}
