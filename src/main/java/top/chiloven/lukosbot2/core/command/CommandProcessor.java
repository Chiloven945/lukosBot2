package top.chiloven.lukosbot2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.IProcessor;
import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.util.message.TextExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Command processor based on Mojang Brigadier.
 *
 * <p>This processor extracts the command line from an {@link InboundMessage} (caption first,
 * otherwise first text part), checks prefix, then executes the Brigadier dispatcher.</p>
 */
@Service
@Log4j2
public class CommandProcessor implements IProcessor {

    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final String prefix;

    public CommandProcessor(List<IBotCommand> commands, AppProperties props) {
        String p = props == null ? "/" : props.getPrefix();
        this.prefix = (p == null || p.isBlank()) ? "/" : p;

        if (commands != null) {
            for (IBotCommand cmd : commands) {
                try {
                    cmd.register(dispatcher);
                    log.info("Registered command: {}", cmd.name());
                } catch (Exception e) {
                    log.warn("Failed to register command {}: {}", cmd.name(), e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public List<OutboundMessage> handle(InboundMessage in) {
        if (in == null) return List.of();

        String raw = TextExtractor.primaryText(in).trim();
        if (raw.isEmpty()) return List.of();

        if (!raw.startsWith(prefix)) {
            return List.of();
        }

        String cmdLine = raw.substring(prefix.length()).trim();
        if (cmdLine.isEmpty()) return List.of();

        List<OutboundMessage> outs = new ArrayList<>();
        CommandSource src = CommandSource.forInbound(in, outs::add);

        try {
            dispatcher.execute(cmdLine, src);
        } catch (CommandSyntaxException e) {
            // Provide a friendly error with cursor indicator.
            String input = e.getInput() == null ? cmdLine : e.getInput();
            int cursor = Math.max(0, e.getCursor());
            String pointer = " ".repeat(Math.min(cursor, input.length())) + "^";
            src.reply("命令语法错误：\n" + input + "\n" + pointer + "\n" + e.getMessage());
        } catch (Exception e) {
            log.warn("Command execution error: {}", e.getMessage(), e);
            src.reply("命令执行失败：" + e.getClass().getSimpleName());
        }

        return outs;
    }

}
