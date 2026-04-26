package top.chiloven.lukosbot2.core.cli;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.cli.ICliCommand;
import top.chiloven.lukosbot2.util.brigadier.BrigadierUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class CliCmdProcessor {

    private final CommandDispatcher<CliCmdContext> dispatcher = new CommandDispatcher<>();
    private final Map<String, ICliCommand> commands = new HashMap<>();

    public CliCmdProcessor(List<ICliCommand> cliCommands) {
        if (cliCommands != null) {
            for (ICliCommand cliCmd : cliCommands) {
                try {
                    cliCmd.register(dispatcher);
                    BrigadierUtils.registerAliases(dispatcher, cliCmd.name(), cliCmd.aliases());
                    commands.put(cliCmd.name(), cliCmd);
                    log.info("[Cli] Registered cli command: {}", cliCmd.name() + (cliCmd.aliases().isEmpty() ? "" : " aliases: " + cliCmd.aliases()));
                } catch (Exception e) {
                    log.error("[Cli] Failed to register cli command {}: {}", cliCmd.name(), e.getMessage(), e);
                }
            }
        }
    }

    public void handle(String line, CliCmdContext ctx) {
        var parseResults = dispatcher.parse(line, ctx);

        String commandName = null;
        if (!parseResults.getContext().getNodes().isEmpty()) {
            commandName = parseResults.getContext().getNodes().getFirst().getNode().getName();
        }

        try {
            dispatcher.execute(parseResults);
        } catch (CommandSyntaxException e) {
            String input = e.getInput() == null ? line : e.getInput();
            String pointer = " ".repeat(Math.clamp(e.getCursor(), 0, input.length())) + "^";

            StringBuilder errorMsg = new StringBuilder()
                    .append("Syntax error: \n").append(input).append("\n")
                    .append(pointer).append("\n")
                    .append(e.getMessage());

            if (commandName != null) {
                ICliCommand cmd = commands.get(commandName);
                if (cmd != null) {
                    errorMsg.append("\nUsage: ").append(cmd.usage());
                }
            }

            ctx.printlnErr(errorMsg.toString());
        } catch (Exception e) {
            log.warn("[Cli] Cli command execution error: {}", e.getMessage(), e);
            ctx.printlnErr("Failed to execute CLI command: " + e.getMessage() + ". More information can be viewed above.");
        }
    }

}
