package chiloven.lukosbot2.bootstrap;

import chiloven.lukosbot2.commands.EchoCommand;
import chiloven.lukosbot2.commands.HelpCommand;
import chiloven.lukosbot2.commands.MotdCommand;
import chiloven.lukosbot2.commands.PingCommand;
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
            CommandRegistry registry = new CommandRegistry()
                    .add(
                            new BilibiliCommand(),
                            new EchoCommand(),
                            new GitHubCommand(config.getGitHub().getToken()),
                            new McWikiCommand(),
                            new MotdCommand(),
                            new MusicCommand(config.getMusic()),
                            new PingCommand(),
                            new WikiCommand()
                    );
            registry.add(new HelpCommand(registry, props.getPrefix()));
            return registry;
        } catch (Exception e) {
            throw new BootStepException(2, "Failed to register commands", e);
        }
    }

    ///  Build the message processing pipeline with prefix guard and command processor
    public PipelineProcessor buildPipeline(CommandRegistry registry) {
        try {
            CommandProcessor cmd = new CommandProcessor(props.getPrefix(), registry);
            return new PipelineProcessor(PipelineProcessor.Mode.STOP_ON_FIRST)
                    .add(cmd);
        } catch (Exception e) {
            throw new BootStepException(3, "Failed to build the message processing pipeline", e);
        }
    }

}
