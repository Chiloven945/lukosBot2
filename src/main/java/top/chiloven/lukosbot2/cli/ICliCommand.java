package top.chiloven.lukosbot2.cli;

import com.mojang.brigadier.CommandDispatcher;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;

public interface ICliCommand {

    String name();

    String description();

    String usage();

    void register(CommandDispatcher<CliCmdContext> dispatcher);

}
