package top.chiloven.lukosbot2.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Render {@link UsageNode} into markdown text (for chat) or structured lines (for image rendering).
 */
public final class UsageTextRenderer {

    private UsageTextRenderer() {
    }

    public static String renderMarkdown(UsageNode node, Options options) {
        return render(node, options).markdownText();
    }

    public static Result render(UsageNode node, Options options) {
        if (node == null) return new Result("", List.of());
        Objects.requireNonNull(options, "options");

        List<RenderedLine> lines = new ArrayList<>();

        if (options.includeHeader()) {
            String cmd = joinInvocation(options.prefix(), List.of(node.getName()), "");
            lines.add(RenderedLine.text(LineKind.TITLE, "命令：%s".formatted(cmd)));

            if (options.includeDescriptionInHeader() && !isBlank(node.getDescription())) {
                lines.add(RenderedLine.text(LineKind.TEXT, node.getDescription()));
            }
            lines.add(RenderedLine.blank());
        }

        if (options.includeUsageSection()) {
            lines.add(RenderedLine.text(LineKind.HEADING, "用法："));
            List<SyntaxEntry> all = new ArrayList<>();
            collectSyntax(node, List.of(node.getName()), 0, options.maxDepth(), all);

            if (all.isEmpty()) {
                String inv = joinInvocation(options.prefix(), List.of(node.getName()), "");
                lines.add(codeLine(inv, "", options));
            } else {
                for (SyntaxEntry s : all) {
                    String inv = joinInvocation(options.prefix(), s.path, s.tail);
                    lines.add(codeLine(inv, s.desc, options));
                }
            }
            lines.add(RenderedLine.blank());
        }

        if (options.includeParametersSection()) {
            List<NodeAtPath> withParams = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getParameters().isEmpty());
            if (!withParams.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "参数："));
                for (NodeAtPath np : withParams) {
                    if (np.path.size() > 1) {
                        lines.add(RenderedLine.text(LineKind.TEXT,
                                "在 %s 下：".formatted(codeToken(joinInvocation(options.prefix(), np.path, ""), options))));
                    }
                    for (UsageNode.Parameter p : np.node.getParameters()) {
                        String token = UsageNode.renderItem(p.token());
                        lines.add(bulletKeyValueLine(token, p.description(), options));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeOptionsSection()) {
            List<NodeAtPath> withOpts = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getOptions().isEmpty());
            if (!withOpts.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "选项："));
                for (NodeAtPath np : withOpts) {
                    if (np.path.size() > 1) {
                        lines.add(RenderedLine.text(LineKind.TEXT,
                                "在 %s 下：".formatted(codeToken(joinInvocation(options.prefix(), np.path, ""), options))));
                    }
                    for (UsageNode.Option o : np.node.getOptions()) {
                        String token = UsageNode.renderItem(o.token());
                        lines.add(bulletKeyValueLine(token, o.description(), options));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeExamplesSection()) {
            List<NodeAtPath> withEx = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getExamples().isEmpty());
            if (!withEx.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "示例："));
                for (NodeAtPath np : withEx) {
                    if (np.path.size() > 1) {
                        lines.add(RenderedLine.text(LineKind.TEXT,
                                "在 %s 下：".formatted(codeToken(joinInvocation(options.prefix(), np.path, ""), options))));
                    }
                    for (String ex : np.node.getExamples()) {
                        String normalized = normalizeExample(ex, options);
                        lines.add(codeLine(normalized, "", options));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeNotesSection()) {
            List<NodeAtPath> withNotes = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getNotes().isEmpty());
            if (!withNotes.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "说明："));
                for (NodeAtPath np : withNotes) {
                    if (np.path.size() > 1) {
                        lines.add(RenderedLine.text(LineKind.TEXT,
                                "在 %s 下：".formatted(codeToken(joinInvocation(options.prefix(), np.path, ""), options))));
                    }
                    for (String note : np.node.getNotes()) {
                        lines.add(RenderedLine.text(LineKind.TEXT, "• " + note));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeSubcommandsSection() && !node.getChildren().isEmpty()) {
            lines.add(RenderedLine.text(LineKind.HEADING, "子命令："));
            for (UsageNode c : node.getChildren()) {
                String inv = joinInvocation(options.prefix(), List.of(node.getName(), c.getName()), "");
                lines.add(codeLine(inv, c.getDescription(), options));
            }
            lines.add(RenderedLine.blank());
        }

        // trim trailing blanks
        while (!lines.isEmpty() && lines.getLast().kind() == LineKind.BLANK) {
            lines.removeLast();
        }

        StringBuilder md = new StringBuilder();
        for (RenderedLine line : lines) {
            md.append(line.markdown() == null ? "" : line.markdown()).append("\n");
        }
        return new Result(md.toString().trim(), List.copyOf(lines));
    }

    private static void collectSyntax(UsageNode node, List<String> path, int depth, int maxDepth, List<SyntaxEntry> out) {
        if (node == null) return;
        if (depth > maxDepth) return;

        for (UsageNode.Syntax s : node.getSyntaxes()) {
            out.add(new SyntaxEntry(path, s.tailText(), s.description()));
        }

        for (UsageNode c : node.getChildren()) {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(c.getName());
            collectSyntax(c, List.copyOf(childPath), depth + 1, maxDepth, out);
        }
    }

    private static List<NodeAtPath> collectNodes(UsageNode node, List<String> path, int depth, int maxDepth, Predicate<UsageNode> predicate) {
        List<NodeAtPath> out = new ArrayList<>();
        collectNodes0(node, path, depth, maxDepth, out, predicate);
        return out;
    }

    private static void collectNodes0(UsageNode node, List<String> path, int depth, int maxDepth, List<NodeAtPath> out, Predicate<UsageNode> predicate) {
        if (node == null) return;
        if (depth > maxDepth) return;

        if (predicate.test(node)) {
            out.add(new NodeAtPath(path, node));
        }

        for (UsageNode c : node.getChildren()) {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(c.getName());
            collectNodes0(c, List.copyOf(childPath), depth + 1, maxDepth, out, predicate);
        }
    }

    private static RenderedLine codeLine(String invocation, String desc, Options options) {
        String commentPart = isBlank(desc) ? "" : ("  # " + desc);
        String plain = (invocation == null ? "" : invocation) + commentPart;

        String mdInv = options.markdownBackticks() ? ("`" + (invocation == null ? "" : invocation) + "`") : (invocation == null ? "" : invocation);
        String md = mdInv + commentPart;

        return new RenderedLine(LineKind.CODE, md.trim(), plain.trim());
    }

    private static RenderedLine bulletKeyValueLine(String key, String value, Options options) {
        String k = key == null ? "" : key;
        String v = value == null ? "" : value;

        String mdKey = options.markdownBackticks() ? ("`" + k + "`") : k;
        String md = "• " + mdKey + (isBlank(v) ? "" : ("： " + v));
        String plain = "• " + k + (isBlank(v) ? "" : (": " + v));
        return new RenderedLine(LineKind.TEXT, md, plain);
    }

    private static String codeToken(String token, Options options) {
        if (token == null) token = "";
        return options.markdownBackticks() ? ("`" + token + "`") : token;
    }

    private static String joinInvocation(String prefix, List<String> path, String tail) {
        String p = prefix == null ? "" : prefix;
        String core = String.join(" ", path);
        String inv = p + core;
        if (tail != null && !tail.isBlank()) {
            inv = inv + " " + tail.trim();
        }
        return inv.trim();
    }

    private static String normalizeExample(String ex, Options options) {
        if (ex == null) return "";
        String e = ex.trim();
        if (!options.autoPrefixExamples()) return e;

        String p = options.prefix() == null ? "" : options.prefix();

        if (p.isBlank()) return e;
        if (e.startsWith(p)) return e;
        if (p.equals("/") && e.startsWith("/")) return e;
        if (e.startsWith(" ")) e = e.trim();

        return p + e;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public enum LineKind {
        TITLE,
        HEADING,
        CODE,
        TEXT,
        BLANK
    }

    public record RenderedLine(LineKind kind, String markdown, String plain) {
        public static RenderedLine blank() {
            return new RenderedLine(LineKind.BLANK, "", "");
        }

        public static RenderedLine text(LineKind kind, String text) {
            String t = text == null ? "" : text;
            return new RenderedLine(kind, t, t);
        }
    }

    public record Result(String markdownText, List<RenderedLine> lines) {
    }

    /**
     * Rendering options.
     *
     * @param prefix                     command prefix, e.g. "/"
     * @param markdownBackticks          wrap invocations and tokens in backticks
     * @param includeHeader              render "命令：xxx" title line
     * @param includeDescriptionInHeader render description in header block
     * @param includeUsageSection        render "用法" section
     * @param includeParametersSection   render "参数" section
     * @param includeOptionsSection      render "选项/开关" section
     * @param includeExamplesSection     render "示例" section
     * @param includeNotesSection        render "说明" section
     * @param includeSubcommandsSection  render immediate subcommands list
     * @param maxDepth                   max recursion depth for collecting syntaxes/sections
     * @param autoPrefixExamples         prefix examples if they don't start with prefix
     */
    public record Options(
            String prefix,
            boolean markdownBackticks,
            boolean includeHeader,
            boolean includeDescriptionInHeader,
            boolean includeUsageSection,
            boolean includeParametersSection,
            boolean includeOptionsSection,
            boolean includeExamplesSection,
            boolean includeNotesSection,
            boolean includeSubcommandsSection,
            int maxDepth,
            boolean autoPrefixExamples
    ) {
        public static Options forHelp(String prefix) {
            return new Options(
                    prefix,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    8,
                    true
            );
        }

        /**
         * Usage text returned inside a command itself (usually shorter, no header).
         */
        public static Options forCommand(String prefix) {
            return new Options(
                    prefix,
                    true,
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    6,
                    true
            );
        }
    }

    private record SyntaxEntry(List<String> path, String tail, String desc) {
    }

    private record NodeAtPath(List<String> path, UsageNode node) {
    }
}
