package chiloven.lukosbot2;

import chiloven.lukosbot2.bootstrap.Boot;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.MessageDispatcher;
import chiloven.lukosbot2.core.MessageSenderHub;
import chiloven.lukosbot2.core.PipelineProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    static void main(String[] args) {
        log.info("""
                        Starting lukosBot2 {} ...
                         __       __  __  __  __   _____   ____    ____     _____   ______    ___    \s
                        /\\ \\     /\\ \\/\\ \\/\\ \\/\\ \\ /\\  __`\\/\\  _`\\ /\\  _`\\  /\\  __`\\/\\__  _\\ /'___`\\  \s
                        \\ \\ \\    \\ \\ \\ \\ \\ \\ \\/'/'\\ \\ \\/\\ \\ \\,\\L\\_\\ \\ \\L\\ \\\\ \\ \\/\\ \\/_/\\ \\//\\_\\ /\\ \\ \s
                         \\ \\ \\  __\\ \\ \\ \\ \\ \\ , <  \\ \\ \\ \\ \\/_\\__ \\\\ \\  _ <'\\ \\ \\ \\ \\ \\ \\ \\\\/_/// /__\s
                          \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\\\`\\ \\ \\ \\_\\ \\/\\ \\L\\ \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\ \\  // /_\\ \\
                           \\ \\____/ \\ \\_____\\ \\_\\ \\_\\\\ \\_____\\ `\\____\\ \\____/ \\ \\_____\\ \\ \\_\\/\\______/
                            \\/___/   \\/_____/\\/_/\\/_/ \\/_____/\\/_____/\\/___/   \\/_____/  \\/_/\\/_____/\s"""
                , Constants.VERSION
        );
        SpringApplication.run(Main.class, args);
    }

    // 1) Command
    @Bean
    public CommandRegistry commandRegistry(Boot boot) {
        CommandRegistry registry = boot.buildCommands();
        log.info("Commands registered: {}", registry.listCommands());
        return registry;
    }

    // 2) Pipeline
    @Bean
    public PipelineProcessor pipelineProcessor(Boot boot, CommandRegistry registry) {
        PipelineProcessor pipeline = boot.buildPipeline(registry);
        log.info("Message processing pipeline built.");
        return pipeline;
    }

    // 3) MessageSenderHub
    @Bean
    public MessageSenderHub senderMux() {
        return new MessageSenderHub();
    }

    // 4) MessageDispatcher
    @Bean
    public MessageDispatcher router(PipelineProcessor pipeline, MessageSenderHub msh) {
        MessageDispatcher md = new MessageDispatcher(pipeline, msh);
        log.info("Outbound routing set up.");
        return md;
    }

}
