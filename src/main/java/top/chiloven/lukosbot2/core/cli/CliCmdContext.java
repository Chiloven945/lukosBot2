package top.chiloven.lukosbot2.core.cli;

import org.jspecify.annotations.NonNull;

import java.io.PrintStream;

import static top.chiloven.lukosbot2.util.StringUtils.formatStackTrace;
import static top.chiloven.lukosbot2.util.StringUtils.resolveColorCode;

/**
 * Small wrapper around a CLI output stream used by console commands.
 *
 * <p>This class centralizes three behaviors:</p>
 * <ul>
 *   <li>writing to the configured {@link PrintStream}</li>
 *   <li>translating inline color codes before output</li>
 *   <li>formatting exception stack traces for error reporting</li>
 * </ul>
 *
 * <p>The {@code Raw} methods bypass color-code resolution, while the regular methods interpret
 * formatting markers via {@link #print(String)} / {@link #println(String)} / {@link #printf(String, Object...)}.</p>
 */
public final class CliCmdContext {

    private final PrintStream out;

    /**
     * Creates a CLI context bound to one output stream.
     *
     * @param out destination stream used by CLI commands.
     */
    public CliCmdContext(PrintStream out) {
        this.out = out;
    }

    /**
     * Prints an error message without a trailing line break.
     *
     * @param string message text.
     */
    public void printErr(@NonNull String string) {
        print("§4" + string + "§r");
    }

    /**
     * Prints formatted text without a trailing line break.
     *
     * @param string text with optional color codes.
     */
    public void print(@NonNull String string) {
        out.print(resolveColorCode(string));
    }

    /**
     * Prints an error message and stack trace without a trailing line break.
     *
     * @param string error summary.
     * @param e      exception to render.
     */
    public void printErr(@NonNull String string, @NonNull Exception e) {
        print("§4" + string + "\n" + formatStackTrace(e) + "§r");
    }

    /**
     * Prints an error message followed by a line break.
     *
     * @param string message text.
     */
    public void printlnErr(@NonNull String string) {
        println("§4" + string + "§r");
    }

    /**
     * Prints formatted text followed by a line break.
     *
     * @param string text with optional color codes.
     */
    public void println(@NonNull String string) {
        out.println(resolveColorCode(string));
    }

    /**
     * Prints an error message and stack trace followed by a line break.
     *
     * @param string error summary.
     * @param e      exception to render.
     */
    public void printlnErr(@NonNull String string, @NonNull Exception e) {
        println("§4" + string + "\n" + formatStackTrace(e) + "§r");
    }

    /**
     * Prints a formatted error message without automatically appending a line break.
     *
     * @param format format string.
     * @param args   format arguments.
     */
    public void printfErr(@NonNull String format, Object... args) {
        printf("§4" + format + "§r", args);
    }

    /**
     * Prints formatted text without automatically appending a line break.
     *
     * @param format format string.
     * @param args   format arguments.
     */
    public void printf(@NonNull String format, Object... args) {
        out.printf(resolveColorCode(format), args);
    }

    /**
     * Prints a formatted error message plus stack trace without automatically appending a line break.
     *
     * @param format error summary format string.
     * @param e      exception to render.
     * @param args   format arguments.
     */
    public void printfErr(@NonNull String format, @NonNull Exception e, Object... args) {
        printf("§4" + format + "\n" + formatStackTrace(e) + "§r", args);
    }

    /**
     * Prints raw text without color-code resolution.
     *
     * @param string raw text.
     */
    public void printRaw(@NonNull String string) {
        out.print(string);
    }

    /**
     * Prints raw text followed by a line break, without color-code resolution.
     *
     * @param string raw text.
     */
    public void printlnRaw(@NonNull String string) {
        out.println(string);
    }

    /**
     * Prints raw formatted text without color-code resolution.
     *
     * @param format raw format string.
     * @param args   format arguments.
     */
    public void printfRaw(@NonNull String format, Object... args) {
        out.printf(format, args);
    }

}
