package chiloven.lukosbot2.core;

import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class CommandProcessor implements Processor {

    private final AppProperties props;
    private final CommandRegistry registry;
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    public CommandProcessor(AppProperties props, CommandRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    @PostConstruct
    void init() {
        registry.registerAll(dispatcher);
        log.info("Brigadier registered {} commands: {}",
                registry.all().size(), registry.listCommands());
    }

    /**
     * Handle a message, executing commands if the prefix matches.
     *
     * @param in the incoming message
     * @return a list of outgoing messages as command responses, or an empty list if no command was executed
     */
    @Override
    public List<MessageOut> handle(MessageIn in) {
        String prefix = props.getPrefix();
        String t = in.text() == null ? "" : in.text().trim();
        if (!t.startsWith(prefix)) return List.of();

        String cmdLine = t.substring(prefix.length()).trim();
        List<MessageOut> outs = new ArrayList<>();
        CommandSource src = new CommandSource(in, outs::add);

        try {
            dispatcher.execute(cmdLine, src);
        } catch (CommandSyntaxException e) {
            outs.add(MessageOut.text(in.addr(), "命令错误: " + e.getRawMessage().getString()));
        } catch (Exception e) {
            outs.add(MessageOut.text(in.addr(), "命令执行异常: " + e.getClass().getSimpleName()));
        }
        return outs;
    }
}
