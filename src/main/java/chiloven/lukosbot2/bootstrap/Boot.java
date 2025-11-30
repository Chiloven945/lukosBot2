package chiloven.lukosbot2.bootstrap;

import chiloven.lukosbot2.commands.*;
import chiloven.lukosbot2.commands.bilibili.BilibiliCommand;
import chiloven.lukosbot2.commands.github.GitHubCommand;
import chiloven.lukosbot2.commands.music.MusicCommand;
import chiloven.lukosbot2.commands.wikis.McWikiCommand;
import chiloven.lukosbot2.commands.wikis.WikiCommand;
import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.config.CommandConfig;
import chiloven.lukosbot2.core.CommandProcessor;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.PipelineProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

///  Bootstrap helper to build commands, pipeline, and start platforms
@Component
@RequiredArgsConstructor
public final class Boot {

    private final AppProperties props;
    private final CommandConfig config;

    ///  Register commands; return the command registry
    public CommandRegistry buildCommands() {
        try {
            CommandRegistry registry = new CommandRegistry();

            // Commands to be displayed in the /help command.
            registry.add(
                    new BilibiliCommand(),
                    new EchoCommand(),
                    new GitHubCommand(config.getGitHub().getToken()),
                    new IpCommand(),
                    new McWikiCommand(),
                    new MotdCommand(),
                    new MusicCommand(config.getMusic()),
                    new PingCommand(),
                    new PlayerCommand(),
                    new WikiCommand(),
                    new WhoisCommand()
            );

            // Pass the registry.
            registry.add(new HelpCommand(registry, props.getPrefix()));

            // Commands that should not be displayed.
            registry.add(
                    new StartCommand()
            );

            return registry;
        } catch (Exception e) {
            throw new BootStepException(2, "Failed to register commands", e);
        }
    }

    ///  Build the message processing pipeline with prefix guard and command processor
    public PipelineProcessor buildPipeline(CommandRegistry registry) {
        try {
            CommandProcessor cmd = new CommandProcessor(props.getPrefix(), registry);
            return new PipelineProcessor(PipelineProcessor.Mode.STOP_ON_FIRST).add(cmd);
        } catch (Exception e) {
            throw new BootStepException(3, "Failed to build the message processing pipeline", e);
        }
    }

}
