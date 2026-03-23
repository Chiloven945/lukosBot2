package top.chiloven.lukosbot2.core.cli;

import org.jspecify.annotations.NonNull;

import java.io.PrintStream;

import static top.chiloven.lukosbot2.util.StringUtils.resolveColorCode;

public final class CliCmdContext {

    private final PrintStream out;

    public CliCmdContext(PrintStream out) {
        this.out = out;
    }

    public void printErr(@NonNull String string) {
        print("§c" + string + "§r");
    }

    public void print(@NonNull String string) {
        out.print(resolveColorCode(string));
    }

    public void printlnErr(@NonNull String string) {
        println("§c" + string + "§r");
    }

    public void println(@NonNull String string) {
        out.println(resolveColorCode(string));
    }

    public void printfErr(@NonNull String format, Object... args) {
        printf("§c" + format + "§r", args);
    }

    public void printf(@NonNull String format, Object... args) {
        out.printf(resolveColorCode(format), args);
    }

    public void printRaw(@NonNull String string) {
        out.print(string);
    }

    public void printlnRaw(@NonNull String string) {
        out.println(string);
    }

    public void printfRaw(@NonNull String format, Object... args) {
        out.printf(format, args);
    }

}
