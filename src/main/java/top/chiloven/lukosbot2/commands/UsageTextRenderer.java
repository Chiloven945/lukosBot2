package top.chiloven.lukosbot2.commands;

import java.util.*;
import java.util.function.Predicate;

/**
 * Render {@link UsageNode} into markdown text (for chat) or structured lines (for image rendering).
 *
 * <p>This renderer intentionally keeps the output compact and high-signal:
 * large command title, short summary, a concise “快速使用” section, a merged “参数与选项” section, examples, tips, and
 * subcommands.</p>
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
            lines.add(RenderedLine.text(LineKind.TITLE, cmd));

            if (options.includeDescriptionInHeader() && !isBlank(node.getDescription())) {
                lines.add(RenderedLine.text(LineKind.SUBTITLE, node.getDescription()));
            }

            List<String> aliases = node.getAliases();
            if (aliases != null && !aliases.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.SUBTITLE, "别名： " + String.join(" · ", aliases)));
            }

            lines.add(RenderedLine.blank());
        }

        if (options.includeUsageSection()) {
            List<SyntaxEntry> all = new ArrayList<>();
            collectSyntax(node, List.of(node.getName()), 0, options.maxDepth(), all);

            lines.add(RenderedLine.text(LineKind.HEADING, "快速使用"));
            Set<String> seenInvocations = new LinkedHashSet<>();

            if (all.isEmpty()) {
                String inv = joinInvocation(options.prefix(), List.of(node.getName()), "");
                lines.add(codeLine(inv, options));
            } else {
                for (SyntaxEntry s : all) {
                    String inv = joinInvocation(options.prefix(), s.path(), s.tail());
                    if (!seenInvocations.add(inv)) continue;
                    lines.add(codeLine(inv, options));
                    if (!isBlank(s.desc())) {
                        lines.add(RenderedLine.text(LineKind.TEXT, s.desc().trim()));
                    }
                }
            }
            lines.add(RenderedLine.blank());
        }

        if (options.includeParametersSection() || options.includeOptionsSection()) {
            List<NodeAtPath> withDocs = collectNodes(
                    node,
                    List.of(node.getName()),
                    0,
                    options.maxDepth(),
                    n -> (options.includeParametersSection() && !n.getParameters().isEmpty())
                            || (options.includeOptionsSection() && !n.getOptions().isEmpty())
            );

            if (!withDocs.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "参数与选项"));
                boolean showPathLabel = withDocs.size() > 1 || withDocs.stream().anyMatch(np -> np.path().size() > 1);

                for (NodeAtPath np : withDocs) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix(), np.path(), "")));
                    }

                    if (options.includeParametersSection()) {
                        for (UsageNode.Parameter p : np.node().getParameters()) {
                            String token = UsageNode.renderItem(p.token());
                            lines.add(keyValueBullet(token, p.description(), options));
                        }
                    }

                    if (options.includeOptionsSection()) {
                        for (UsageNode.Option o : np.node().getOptions()) {
                            String token = UsageNode.renderItem(o.token());
                            lines.add(keyValueBullet(token, o.description(), options));
                        }
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeExamplesSection()) {
            List<NodeAtPath> withEx = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getExamples()
                    .isEmpty());
            if (!withEx.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "示例"));
                boolean showPathLabel = withEx.size() > 1 || withEx.stream().anyMatch(np -> np.path().size() > 1);

                for (NodeAtPath np : withEx) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix(), np.path(), "")));
                    }
                    for (String ex : np.node().getExamples()) {
                        lines.add(codeLine(normalizeExample(ex, options), options));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeNotesSection()) {
            List<NodeAtPath> withNotes = collectNodes(node, List.of(node.getName()), 0, options.maxDepth(), n -> !n.getNotes()
                    .isEmpty());
            if (!withNotes.isEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "提示"));
                boolean showPathLabel = withNotes.size() > 1 || withNotes.stream().anyMatch(np -> np.path().size() > 1);

                for (NodeAtPath np : withNotes) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix(), np.path(), "")));
                    }
                    for (String note : np.node().getNotes()) {
                        lines.add(RenderedLine.text(LineKind.BULLET, note));
                    }
                }
                lines.add(RenderedLine.blank());
            }
        }

        if (options.includeSubcommandsSection() && !node.getChildren().isEmpty()) {
            lines.add(RenderedLine.text(LineKind.HEADING, "子命令"));
            for (UsageNode c : node.getChildren()) {
                String inv = joinInvocation(options.prefix(), List.of(node.getName(), c.getName()), "");
                String desc = c.getDescription() == null ? "" : c.getDescription().trim();
                String plain = isBlank(desc) ? inv : (inv + " — " + desc);
                String mdInv = options.markdownBackticks() ? ("`" + inv + "`") : inv;
                String md = isBlank(desc) ? mdInv : (mdInv + " — " + desc);
                lines.add(new RenderedLine(LineKind.BULLET, "• " + md, "• " + plain));
            }
            lines.add(RenderedLine.blank());
        }

        while (!lines.isEmpty() && lines.getLast().kind() == LineKind.BLANK) {
            lines.removeLast();
        }

        StringBuilder md = new StringBuilder();
        for (RenderedLine line : lines) {
            md.append(line.markdown() == null ? "" : line.markdown()).append("\n");
        }
        return new Result(md.toString().trim(), List.copyOf(lines));
    }

    private static void collectSyntax(
            UsageNode node, List<String> path, int depth, int maxDepth, List<SyntaxEntry> out) {
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

    private static List<NodeAtPath> collectNodes(
            UsageNode node, List<String> path, int depth, int maxDepth, Predicate<UsageNode> predicate) {
        List<NodeAtPath> out = new ArrayList<>();
        collectNodes0(node, path, depth, maxDepth, out, predicate);
        return out;
    }

    private static void collectNodes0(
            UsageNode node, List<String> path, int depth, int maxDepth, List<NodeAtPath> out,
            Predicate<UsageNode> predicate
    ) {
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

    private static RenderedLine codeLine(String invocation, Options options) {
        String plain = invocation == null ? "" : invocation.trim();
        String md = options.markdownBackticks() ? ("`" + plain + "`") : plain;
        return new RenderedLine(LineKind.CODE, md, plain);
    }

    private static RenderedLine keyValueBullet(String key, String value, Options options) {
        String k = key == null ? "" : key.trim();
        String v = value == null ? "" : value.trim();

        String plain = isBlank(v) ? k : (k + " — " + v);
        String mdKey = options.markdownBackticks() ? ("`" + k + "`") : k;
        String md = isBlank(v) ? mdKey : (mdKey + " — " + v);
        return new RenderedLine(LineKind.BULLET, "• " + md, "• " + plain);
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
        SUBTITLE,
        HEADING,
        LABEL,
        CODE,
        TEXT,
        BULLET,
        BLANK
    }

    public record RenderedLine(
            LineKind kind,
            String markdown,
            String plain
    ) {

        public static RenderedLine blank() {
            return new RenderedLine(LineKind.BLANK, "", "");
        }

        public static RenderedLine text(LineKind kind, String text) {
            String t = text == null ? "" : text;
            return new RenderedLine(kind, t, t);
        }

    }

    public record Result(
            String markdownText,
            List<RenderedLine> lines
    ) {

    }

    /**
     * Rendering options.
     *
     * @param prefix                     command prefix, e.g. "/"
     * @param markdownBackticks          wrap invocations and tokens in backticks
     * @param includeHeader              render title block
     * @param includeDescriptionInHeader render description in header block
     * @param includeUsageSection        render “快速使用” section
     * @param includeParametersSection   render parameters section
     * @param includeOptionsSection      render options section
     * @param includeExamplesSection     render examples section
     * @param includeNotesSection        render notes/tips section
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

    private record SyntaxEntry(
            List<String> path,
            String tail,
            String desc
    ) {

    }

    private record NodeAtPath(
            List<String> path,
            UsageNode node
    ) {

    }

}
