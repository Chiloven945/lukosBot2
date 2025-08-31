package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class CommandProcessor implements Processor {
    private static final Logger log = LogManager.getLogger(CommandProcessor.class);

    private final String prefix;
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    public CommandProcessor(String prefix, CommandRegistry registry) {
        this.prefix = prefix;
        registry.registerAll(dispatcher);
        log.info("Brigadier registered {} commands", registry.all().size());
    }

    /**
     * Handle a message, executing commands if the prefix matches.
     *
     * @param in the incoming message
     * @return a list of outgoing messages as command responses, or an empty list if no command was executed
     */
    @Override
    public List<MessageOut> handle(MessageIn in) {
        String t = in.text() == null ? "" : in.text().trim();
        if (!t.startsWith(prefix)) return List.of();

        String cmdLine = t.substring(prefix.length()).trim();
        List<MessageOut> outs = new ArrayList<>();
        CommandSource src = new CommandSource(in, outs::add);

        try {
            dispatcher.execute(cmdLine, src);
        } catch (CommandSyntaxException e) {
            outs.add(MessageOut.replyTo(in, "命令错误: " + e.getRawMessage().getString()));
        } catch (Exception e) {
            outs.add(MessageOut.replyTo(in, "命令执行异常: " + e.getClass().getSimpleName()));
        }
        return outs;
    }
}
