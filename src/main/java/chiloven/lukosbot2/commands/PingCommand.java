package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PingCommand implements BotCommand {
    @Override
    public String name() {
        return "ping";
    }

    /**
     * Format uptime in seconds to d:hh:mm:ss
     *
     * @param seconds uptime in seconds
     * @return formatted uptime string
     */
    private static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d:%02d:%02d", days, hours, minutes, secs);
    }

    @Override
    public String usage() {
        return """
                用法：
                /ping
                """;
    }

    @Override
    public String description() {
        return "健康检查：返回 pong 及运行状态";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(buildStatus());
                            return 1;
                        })
        );
    }

    private String buildStatus() {
        Runtime runtime = Runtime.getRuntime();
        long freeMem = runtime.freeMemory() / 1024 / 1024;
        long totalMem = runtime.totalMemory() / 1024 / 1024;
        long maxMem = runtime.maxMemory() / 1024 / 1024;
        int cores = runtime.availableProcessors();

        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
        long uptimeSec = rtBean.getUptime() / 1000;
        String uptimeFmt = formatUptime(uptimeSec);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());

        return String.format("""
                        Pong 🏓
                        时间: %s
                        运行时间: %s
                        CPU 核心: %d
                        系统: %s %s
                        负载: %.2f
                        内存: 已用 %d MB / 总 %d MB (最大 %d MB)
                        线程数: %d
                        """,
                time,
                uptimeFmt,
                cores,
                osBean.getName(), osBean.getVersion(),
                osBean.getSystemLoadAverage(),
                (totalMem - freeMem), totalMem, maxMem,
                Thread.activeCount()
        );
    }
}
