package top.chiloven.lukosbot2.core.command.cli;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.ICliCommand;

import static top.chiloven.lukosbot2.util.StringUtils.indexOfWhitespace;

@Service
@Log4j2
public class CliCmdProcessor {

    private final CliCmdRegistry registry;

    public CliCmdProcessor(CliCmdRegistry registry) {
        this.registry = registry;
        for (ICliCommand cmd : registry.all()) {
            log.info("[Cli] Registered cli command: {}",
                    cmd.name() + (cmd.aliases().isEmpty() ? "" : " aliases: " + cmd.aliases()));
        }
    }

    public void handle(String line, CliCmdContext ctx) {
        if (line == null || line.isBlank()) return;

        String commandName = firstToken(line);
        ICliCommand cmd = registry.get(commandName);
        if (cmd == null) {
            ctx.printlnErr("Unknown CLI command: " + commandName);
            return;
        }

        try {
            CliCommandRuntime.INSTANCE.execute(cmd, ctx, line);
        } catch (Exception e) {
            log.warn("[Cli] Cli command execution error: {}", e.getMessage(), e);
            ctx.printlnErr("Failed to execute CLI command: " + e.getMessage(), e);
        }
    }

    private static String firstToken(String input) {
        String trimmed = input.trim();
        int ws = indexOfWhitespace(trimmed);
        return ws < 0 ? trimmed : trimmed.substring(0, ws);
    }

}
