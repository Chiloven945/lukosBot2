package chiloven.lukosbot2.bootstrap;

import chiloven.lukosbot2.commands.*;
import chiloven.lukosbot2.commands.github.GitHubCommand;
import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.core.CommandProcessor;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.PipelineProcessor;
import chiloven.lukosbot2.core.PrefixGuardProcessor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

///  Bootstrap helper to build commands, pipeline, and start platforms
@Component
@RequiredArgsConstructor
public final class Boot {
    private static final Logger log = LogManager.getLogger(Boot.class);

    private final AppProperties props;

    ///  Register commands; return the command registry
    public CommandRegistry buildCommands() {
        try {
            CommandRegistry registry = new CommandRegistry()
                    .add(
                            new BilibiliCommand(),
                            new EchoCommand(),
                            new GitHubCommand(props.getGithub().getToken()),
                            new McWikiCommand(),
                            new PingCommand(),
                            new WikiCommand()
                    );
            registry.add(new HelpCommand(registry, props.getPrefix()));
            return registry;
        } catch (Exception e) {
            throw new BootStepError(2, "Failed to register commands", e);
        }
    }

    ///  Build the message processing pipeline with prefix guard and command processor
    public PipelineProcessor buildPipeline(CommandRegistry registry) {
        try {
            CommandProcessor cmd = new CommandProcessor(props.getPrefix(), registry);
            return new PipelineProcessor()
                    .add(new PrefixGuardProcessor(props.getPrefix()))
                    .add(cmd);
        } catch (Exception e) {
            throw new BootStepError(3, "Failed to build the message processing pipeline", e);
        }
    }

}
