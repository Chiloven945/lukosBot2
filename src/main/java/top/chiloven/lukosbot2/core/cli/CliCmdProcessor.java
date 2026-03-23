package top.chiloven.lukosbot2.core.cli;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.cli.ICliCommand;

import java.util.List;

@Service
@Log4j2
public class CliCmdProcessor {

    private final CommandDispatcher<CliCmdContext> dispatcher = new CommandDispatcher<>();

    public CliCmdProcessor(List<ICliCommand> cliCommands) {
        if (cliCommands != null) {
            for (ICliCommand cliCommand : cliCommands) {
                try {
                    cliCommand.register(dispatcher);
                    log.info("[Cli] Registered cli command: {}", cliCommand.name());
                } catch (Exception e) {
                    log.error("[Cli] Failed to register cli command {}: {}", cliCommand.name(), e.getMessage(), e);
                }
            }
        }
    }

    public void handle(String line, CliCmdContext ctx) {
        try {
            dispatcher.execute(line, ctx);
        } catch (CommandSyntaxException e) {
            String input = e.getInput() == null ? line : e.getInput();
            String pointer = " ".repeat(Math.clamp(e.getCursor(), 0, input.length())) + "^";
            ctx.printlnErr("Syntax error: \n" + input + "\n" + pointer + "\n" + e.getMessage());
        } catch (Exception e) {
            log.warn("[Cli] Cli command execution error: {}", e.getMessage(), e);
            ctx.printlnErr("Failed to execute cli command: " + e.getMessage() + ". More information can be viewed above.");
        }
    }

}
