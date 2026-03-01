package top.chiloven.lukosbot2.commands;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a structured, hierarchical “usage specification” for a command, designed to be rendered consistently by a
 * help system (e.g., {@code HelpCommand}) as either plain text or as an image.
 *
 * <p>This type replaces ad-hoc multi-line usage strings with a strongly-typed usage tree, so that:
 * <ul>
 *   <li>Command authors can compose usage from reusable, validated building blocks.</li>
 *   <li>Help renderers can generate predictable output (headings, syntax lines, parameters, options,
 *       examples, notes), and support multiple output formats.</li>
 * </ul>
 *
 * <h2>Conceptual model</h2>
 * <p>A {@code UsageNode} describes one command node (typically one literal in a Brigadier tree),
 * optionally with child nodes (subcommands). Each node can define:
 * <ul>
 *   <li><b>Syntax lines</b>: one or more usage patterns for invoking this node.</li>
 *   <li><b>Parameters</b>: placeholders to be substituted by user-provided values.</li>
 *   <li><b>Options</b>: flags/switches that modify behavior.</li>
 *   <li><b>Examples</b>: concrete invocation examples.</li>
 *   <li><b>Notes</b>: additional free-form hints or caveats.</li>
 * </ul>
 *
 * <h2>Integrated syntax DSL</h2>
 * <p>To avoid hand-writing angle brackets and brackets, {@code UsageNode} provides a small AST
 * and builder-style DSL to create syntax items:
 *
 * <h3>Input items</h3>
 * <ul>
 *   <li>{@link #lit(String)}: a literal token typed as-is.</li>
 *   <li>{@link #arg(String)}: a parameter token rendered as {@code <name>}.</li>
 * </ul>
 *
 * <h3>Modifiers</h3>
 * <ul>
 *   <li>{@link #opt(Item)}: optional item, rendered as {@code [item]}.</li>
 *   <li>{@link #oneOf(Item...)}: required choice, rendered as {@code (a|b)}.</li>
 *   <li>{@link #optOneOf(Item...)}: optional choice, rendered as {@code [a|b]}.</li>
 * </ul>
 *
 * <h3>Grouping and concatenation</h3>
 * <ul>
 *   <li>{@link #group(Item...)}: groups items into a space-separated sequence, useful inside
 *       {@link Opt} or {@link Choice}.</li>
 *   <li>{@link #concat(Item...)}: concatenates items without spaces to create single tokens such as
 *       {@code --k=<v>}.</li>
 * </ul>
 *
 * <h2>Rendering</h2>
 * <p>The core rendering helpers are:
 * <ul>
 *   <li>{@link #renderItem(Item)}: renders a single item.</li>
 *   <li>{@link #renderItems(List)}: renders a sequence of items separated by spaces.</li>
 * </ul>
 *
 * <p>Higher-level formatting (titles, headings, wrapping, image styling, etc.) is expected to be
 * implemented by a renderer component (e.g., {@code UsageTextRenderer} / {@code UsageImageUtils}).
 *
 * <h2>Immutability</h2>
 * <p>{@code UsageNode} instances are immutable. Use {@link Builder} to construct instances.
 *
 * <h2>Validation</h2>
 * <p>Construction enforces non-blank node names and non-blank argument names. Choice items require
 * at least two options.
 */
@Getter
public final class UsageNode {
    /**
     * Returns the literal name for this usage node.
     *
     * <p>This name is typically the Brigadier literal for the command node, without any global prefix.
     */
    private final String name;
    private final String description;
    private final List<Syntax> syntaxes;
    private final List<Parameter> parameters;
    private final List<Option> options;
    private final List<String> examples;
    private final List<String> notes;
    private final List<UsageNode> children;

    private UsageNode(
            String name,
            String description,
            List<Syntax> syntaxes,
            List<Parameter> parameters,
            List<Option> options,
            List<String> examples,
            List<String> notes,
            List<UsageNode> children
    ) {
        this.name = requireNonBlank(name, "name");
        this.description = description == null ? "" : description.trim();

        this.syntaxes = List.copyOf(syntaxes == null ? List.of() : syntaxes);
        this.parameters = List.copyOf(parameters == null ? List.of() : parameters);
        this.options = List.copyOf(options == null ? List.of() : options);
        this.examples = List.copyOf(examples == null ? List.of() : examples);
        this.notes = List.copyOf(notes == null ? List.of() : notes);
        this.children = List.copyOf(children == null ? List.of() : children);
    }

    private static String requireNonBlank(String s, String field) {
        Objects.requireNonNull(s, field);
        String v = s.trim();
        if (v.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return v;
    }

    /**
     * Creates a builder for a root usage node.
     *
     * <p>The {@code name} is the literal label for the node (typically the Brigadier literal name),
     * and is stored without any command prefix (for example: {@code "wiki"}, {@code "help"}).
     *
     * @param name the literal name for this node; must be non-null and non-blank
     * @return a builder for configuring the node
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank after trimming
     */
    public static Builder root(String name) {
        return new Builder(name);
    }

    /**
     * Creates a literal input item.
     *
     * <p>A literal is rendered exactly as provided (after trimming) and represents a token that must be
     * typed as-is by the user.
     *
     * @param text literal token text; {@code null} is treated as an empty string
     * @return a literal item
     */
    public static Item lit(String text) {
        return new Lit(text);
    }

    /**
     * Creates a parameter placeholder item.
     *
     * <p>When rendered, the placeholder becomes {@code <name>}. The angle brackets are produced
     * automatically; command authors should not manually include them.
     *
     * @param name parameter name (without brackets); must be non-null and non-blank
     * @return an argument item
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank after trimming
     */
    public static Item arg(String name) {
        return new Arg(name);
    }

    /**
     * Wraps an item as optional.
     *
     * <p>When rendered, the result becomes {@code [item]}.
     *
     * @param item the item to mark optional; must be non-null
     * @return an optional wrapper item
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public static Item opt(Item item) {
        return new Opt(item);
    }

    /**
     * Creates a required choice between two or more items.
     *
     * <p>When rendered, the result becomes {@code (a|b|c)}. Exactly one alternative is required.
     * Use {@link #optOneOf(Item...)} for an optional choice.
     *
     * @param options two or more alternative items
     * @return a required choice item
     * @throws IllegalArgumentException if fewer than two options are provided
     */
    public static Item oneOf(Item... options) {
        return new Choice(false, Arrays.asList(options));
    }

    /**
     * Creates an optional choice between two or more items.
     *
     * <p>When rendered, the result becomes {@code [a|b|c]}. If present, exactly one alternative should
     * be used; otherwise the entire choice may be omitted.
     *
     * @param options two or more alternative items
     * @return an optional choice item
     * @throws IllegalArgumentException if fewer than two options are provided
     */
    public static Item optOneOf(Item... options) {
        return new Choice(true, Arrays.asList(options));
    }

    /**
     * Creates a grouped sequence of items.
     *
     * <p>Grouping is rendered as a space-separated sequence of its children, without adding extra
     * delimiters. This is primarily useful when embedding multiple tokens inside {@link Opt} or {@link Choice} so they
     * behave as a single conceptual unit.
     *
     * @param items items to group (may be empty or {@code null})
     * @return a grouped item
     */
    public static Item group(Item... items) {
        return new Group(Arrays.asList(items));
    }

    /**
     * Creates a concatenated item.
     *
     * <p>Concatenation renders all inner items with <em>no</em> spaces. This is useful for building
     * tokens like {@code --k=<v>} where a placeholder is embedded inside a single token.
     *
     * @param items items to concatenate (may be empty or {@code null})
     * @return a concatenation item
     */
    public static Item concat(Item... items) {
        return new Concat(Arrays.asList(items));
    }

    /**
     * Renders a single {@link Item} into the integrated syntax text.
     *
     * <p>This method is the low-level renderer for the usage item AST. It applies the following rules:
     * <ul>
     *   <li>{@link Lit}: returns the literal text.</li>
     *   <li>{@link Arg}: returns {@code <name>}.</li>
     *   <li>{@link Opt}: returns {@code [item]}.</li>
     *   <li>{@link Choice}: returns {@code (a|b)} or {@code [a|b]} depending on optionality.</li>
     *   <li>{@link Group}: renders children via {@link #renderItems(List)} (space-separated).</li>
     *   <li>{@link Concat}: concatenates children with no spaces.</li>
     * </ul>
     *
     * <p>Unknown item implementations fall back to {@link Object#toString()}.
     *
     * @param item the item to render; {@code null} yields an empty string
     * @return rendered syntax text for the item (never {@code null})
     */
    public static String renderItem(Item item) {
        return switch (item) {
            case null -> "";
            case Lit(String text) -> text;
            case Arg(String name1) -> "<" + name1 + ">";
            case Opt(Item item1) -> "[" + renderItem(item1) + "]";
            case Choice(boolean optional, List<Item> options1) -> {
                String body = options1.stream()
                        .map(UsageNode::renderItem)
                        .filter(s -> s != null && !s.isBlank())
                        .reduce((a, b) -> a + "|" + b)
                        .orElse("");
                yield (optional ? "[" : "(") + body + (optional ? "]" : ")");
            }
            case Group(List<Item> items1) -> renderItems(items1);
            case Concat(List<Item> items) -> items.stream()
                    .map(UsageNode::renderItem)
                    .filter(s -> s != null && !s.isBlank())
                    .reduce("", (a, b) -> a + b);
            default -> item.toString();
        };
    }

    /**
     * Renders a list of items as a space-separated sequence.
     *
     * <p>Blank or empty renderings are skipped. The resulting string is trimmed.
     *
     * @param items items to render; {@code null} or empty yields an empty string
     * @return rendered sequence (never {@code null})
     */
    public static String renderItems(List<Item> items) {
        if (items == null || items.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            String s = renderItem(it);
            if (s == null || s.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(s);
        }

        return sb.toString().trim();
    }

    /**
     * Marker interface for the integrated syntax item AST.
     *
     * <p>Implementations represent renderable units such as literals, argument placeholders, optional
     * segments, choices, groupings, and concatenations.
     *
     * <p>Renderer implementations should primarily use {@link UsageNode#renderItem(Item)} and
     * {@link UsageNode#renderItems(List)} rather than inspecting implementations directly.
     */
    public interface Item {
    }

    /**
     * Represents the “tail” portion of a syntax line.
     *
     * <p>A syntax line is conceptually split into:
     * <ul>
     *   <li>the <em>command path</em> (derived from the node and its ancestors), and</li>
     *   <li>the <em>tail</em> (items after the path).</li>
     * </ul>
     *
     * <p>The tail can be expressed as either a structured item list ({@link ItemTail}) or raw text
     * ({@link RawTail}).
     */
    public interface SyntaxTail {
        String asText();
    }

    /**
     * A literal input item (typed as-is).
     *
     * <p>Literal text is trimmed. A {@code null} input becomes an empty string, which may be skipped
     * by higher-level rendering.
     *
     * @param text literal token text
     */
    public record Lit(String text) implements Item {
        public Lit {
            text = text == null ? "" : text.trim();
        }
    }

    /**
     * A parameter placeholder item rendered as {@code <name>}.
     *
     * <p>The {@code name} is validated to be non-null and non-blank after trimming.
     *
     * @param name argument name (without brackets)
     */
    public record Arg(String name) implements Item {
        /**
         * @throws NullPointerException     if {@code name} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank after trimming
         */
        public Arg {
            name = requireNonBlank(name, "arg");
        }
    }

    /**
     * An optional wrapper item rendered as {@code [item]}.
     *
     * <p>The wrapped {@code item} must be non-null.
     *
     * @param item wrapped item
     */
    public record Opt(Item item) implements Item {
        /**
         * @throws NullPointerException if {@code item} is {@code null}
         */
        public Opt {
            item = Objects.requireNonNull(item, "item");
        }
    }

    /**
     * A choice item representing a selection between multiple alternatives.
     *
     * <p>Rendering:
     * <ul>
     *   <li>If {@code optional == false}, renders as {@code (a|b|...)} (required choice).</li>
     *   <li>If {@code optional == true}, renders as {@code [a|b|...]} (optional choice).</li>
     * </ul>
     *
     * <p>Validation requires at least two options.
     *
     * @param optional whether the entire choice is optional
     * @param options  two or more alternatives
     */
    public record Choice(boolean optional, List<Item> options) implements Item {
        /**
         * @throws IllegalArgumentException if fewer than two options are provided
         */
        public Choice {
            options = List.copyOf(options == null ? List.of() : options);
            if (options.size() < 2) {
                throw new IllegalArgumentException("choice requires at least 2 options");
            }
        }
    }

    /**
     * A grouped sequence of items.
     *
     * <p>Grouping does not introduce its own delimiters; it renders as a space-separated sequence
     * of its inner items. This is useful to treat multiple tokens as a single conceptual unit when embedded inside
     * {@link Opt} or {@link Choice}.
     *
     * @param items grouped items (immutable snapshot)
     */
    public record Group(List<Item> items) implements Item {
        public Group {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    /**
     * A concatenation item.
     *
     * <p>Concatenation renders inner items without spaces, allowing construction of single composite
     * tokens such as {@code --top=<n>} where a placeholder is embedded into a literal token.
     *
     * @param items concatenated items (immutable snapshot)
     */
    public record Concat(List<Item> items) implements Item {
        public Concat {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    /**
     * Raw tail text for a syntax line.
     *
     * <p>This is an “escape hatch” for quickly defining a tail without building an item AST.
     * The text is trimmed and may be empty.
     *
     * <p>Prefer {@link ItemTail} and {@link Builder#syntax(String, Item...)} for structured syntax.
     *
     * @param text raw tail text (after the command path)
     */
    public record RawTail(String text) implements SyntaxTail {
        public RawTail {
            text = text == null ? "" : text.trim();
        }

        @Override
        public String asText() {
            return text;
        }
    }

    /**
     * Tail representation based on a list of {@link Item} instances.
     *
     * <p>This is the recommended representation because it allows consistent rendering of optional
     * segments, choices, parameter placeholders, and concatenation without manually writing bracket syntax.
     *
     * @param items tail items (immutable snapshot)
     */
    public record ItemTail(List<Item> items) implements SyntaxTail {
        public ItemTail {
            items = List.copyOf(items == null ? List.of() : items);
        }

        @Override
        public String asText() {
            return renderItems(items);
        }
    }

    /**
     * A single syntax line describing one way to invoke this node.
     *
     * <p>The {@code tail} describes items after the command path. The {@code description} is an
     * optional per-syntax annotation intended for inline comments or a second column in help output.
     *
     * @param tail        syntax tail representation (never {@code null}; defaults to empty raw tail)
     * @param description optional one-line description (trimmed; may be empty)
     */
    public record Syntax(SyntaxTail tail, String description) {
        public Syntax {
            tail = Objects.requireNonNullElseGet(tail, () -> new RawTail(""));
            description = description == null ? "" : description.trim();
        }

        /**
         * Returns the rendered tail text for this syntax.
         *
         * <p>This method delegates to {@link SyntaxTail#asText()}.
         *
         * @return rendered tail text (never {@code null}; may be empty)
         */
        public String tailText() {
            return tail.asText();
        }
    }

    /**
     * A documented parameter entry for a usage node.
     *
     * <p>The {@code token} is typically an {@link Arg} (or a composite item via {@link Group} /
     * {@link Concat}) that indicates how the parameter appears in syntax lines.
     *
     * @param token       the parameter token item (non-null)
     * @param description parameter description (trimmed; may be empty)
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public record Parameter(Item token, String description) {
        public Parameter {
            token = Objects.requireNonNull(token, "token");
            description = description == null ? "" : description.trim();
        }
    }

    /**
     * A documented option/flag entry for a usage node.
     *
     * <p>The {@code token} is typically a {@link Lit} representing a flag (e.g. {@code -n},
     * {@code --force}) or a composite item (e.g. {@code --k=<v>} via {@link Concat}).
     *
     * @param token       the option token item (non-null)
     * @param description option description (trimmed; may be empty)
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public record Option(Item token, String description) {
        public Option {
            token = Objects.requireNonNull(token, "token");
            description = description == null ? "" : description.trim();
        }
    }

    /**
     * Builder for {@link UsageNode}.
     *
     * <p>The builder collects syntaxes, parameter docs, option docs, examples, notes, and child nodes,
     * then produces an immutable {@link UsageNode} snapshot via {@link #build()}.
     *
     * <p>Builders are intended to be used in a DSL style:
     * <pre>{@code
     * UsageNode node = UsageNode.root("wiki")
     *     .description("Wikipedia tools")
     *     .syntax("Take a screenshot", UsageNode.arg("article"))
     *     .option("-n", "Disable something")
     *     .example("wiki Java")
     *     .build();
     * }</pre>
     *
     * <p>All input strings are trimmed; blank values are typically ignored for examples/notes.
     * Structured items are validated (e.g., argument names must be non-blank).
     */
    public static final class Builder {
        private final String name;
        private final List<Syntax> syntaxes = new ArrayList<>();
        private final List<Parameter> parameters = new ArrayList<>();
        private final List<Option> options = new ArrayList<>();
        private final List<String> examples = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final List<UsageNode> children = new ArrayList<>();
        private String description = "";

        private Builder(String name) {
            this.name = requireNonBlank(name, "name");
        }

        /**
         * Adds a structured syntax line to the node being built (recommended).
         *
         * <p>The syntax “tail” is the part after the command path. The path itself is implied by the node
         * and its ancestors when rendered by a help system.
         *
         * @param description optional per-syntax description (trimmed; may be empty)
         * @param tailItems   tail items to render after the command path (may be {@code null} / empty)
         * @return this builder
         */
        public Builder syntax(String description, Item... tailItems) {
            List<Item> items = tailItems == null ? List.of() : Arrays.asList(tailItems);
            this.syntaxes.add(new Syntax(new ItemTail(items), description));
            return this;
        }

        /**
         * Adds a syntax line using a raw tail string.
         *
         * <p>This method exists as an escape hatch when constructing an item AST is inconvenient.
         * Prefer {@link #syntax(String, Item...)} for consistent rendering of optionals and choices.
         *
         * @param tail        raw tail text after the command path (trimmed; may be empty)
         * @param description optional per-syntax description (trimmed; may be empty)
         * @return this builder
         * @deprecated Use {@link #syntax(String, Item...)} to avoid manual bracket syntax.
         */
        @Deprecated
        public Builder syntax(String tail, String description) {
            this.syntaxes.add(new Syntax(new RawTail(tail), description));
            return this;
        }

        /**
         * Convenience method to add a documented parameter using a placeholder name.
         *
         * <p>This method is equivalent to:
         * <pre>{@code
         * parameter(UsageNode.arg(name), description)
         * }</pre>
         *
         * @param name        placeholder name (without brackets); must be non-blank
         * @param description parameter description (trimmed; may be empty)
         * @return this builder
         * @throws NullPointerException     if {@code name} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank after trimming
         */
        public Builder param(String name, String description) {
            return parameter(arg(name), description);
        }

        /**
         * Adds a documented parameter entry.
         *
         * @param token       parameter token item (non-null)
         * @param description parameter description (trimmed; may be empty)
         * @return this builder
         * @throws NullPointerException if {@code token} is {@code null}
         */
        public Builder parameter(Item token, String description) {
            this.parameters.add(new Parameter(token, description));
            return this;
        }

        /**
         * Convenience method to add a documented option/flag entry using a literal token.
         *
         * <p>This method is equivalent to:
         * <pre>{@code
         * option(UsageNode.lit(flag), description)
         * }</pre>
         *
         * @param flag        literal flag token (e.g. {@code "-n"}, {@code "--force"})
         * @param description option description (trimmed; may be empty)
         * @return this builder
         */
        public Builder option(String flag, String description) {
            return option(lit(flag), description);
        }

        /**
         * Adds a documented option/flag entry.
         *
         * @param token       option token item (non-null)
         * @param description option description (trimmed; may be empty)
         * @return this builder
         * @throws NullPointerException if {@code token} is {@code null}
         */
        public Builder option(Item token, String description) {
            this.options.add(new Option(token, description));
            return this;
        }

        /**
         * Adds an example invocation.
         *
         * <p>Blank examples are ignored. Examples are typically stored without the global command prefix
         * so that renderers can inject the configured prefix consistently.
         *
         * @param example example invocation text
         * @return this builder
         */
        public Builder example(String example) {
            if (example != null && !example.isBlank()) {
                this.examples.add(example.trim());
            }
            return this;
        }

        /**
         * Adds multiple example invocations.
         *
         * <p>{@code null} input is ignored. Blank entries are skipped. Each example is trimmed.
         *
         * @param examples example invocation strings
         * @return this builder
         */
        public Builder example(String... examples) {
            if (examples != null) {
                Arrays.stream(examples)
                        .filter(example -> example != null && !example.isBlank())
                        .map(String::trim)
                        .forEach(this.examples::add);
            }
            return this;
        }

        /**
         * Adds a note line.
         *
         * <p>Blank notes are ignored. Notes are intended for free-form hints and caveats.
         *
         * @param note note text
         * @return this builder
         */
        public Builder note(String note) {
            if (note != null && !note.isBlank()) {
                this.notes.add(note.trim());
            }
            return this;
        }

        /**
         * Adds multiple note lines.
         *
         * <p>{@code null} input is ignored. Blank entries are skipped. Each note is trimmed.
         *
         * @param notes note strings
         * @return this builder
         */
        public Builder note(String... notes) {
            if (notes != null) {
                Arrays.stream(notes)
                        .filter(example -> example != null && !example.isBlank())
                        .map(String::trim)
                        .forEach(this.notes::add);
            }
            return this;
        }

        /**
         * Adds a child {@link UsageNode} (subcommand) to this node.
         *
         * <p>{@code null} values are ignored.
         *
         * @param node child node
         * @return this builder
         */
        public Builder child(UsageNode node) {
            if (node != null) {
                this.children.add(node);
            }
            return this;
        }

        /**
         * Adds multiple child {@link UsageNode} instances (subcommands) to this node.
         *
         * <p>{@code null} input is ignored.
         *
         * @param nodes child nodes
         * @return this builder
         */
        public Builder child(UsageNode... nodes) {
            if (nodes != null) {
                this.children.addAll(Arrays.asList(nodes));
            }
            return this;
        }

        /**
         * Convenience method to create and add a child node using a nested builder specification.
         *
         * <p>This enables a DSL-like style:
         * <pre>{@code
         * UsageNode root = UsageNode.root("wiki")
         *     .subcommand("md", "Convert to Markdown", b -> b
         *         .syntax("Convert an article", UsageNode.arg("article"))
         *         .example("wiki md Java")
         *     )
         *     .build();
         * }</pre>
         *
         * @param name        child node name (non-blank)
         * @param description child node description (trimmed; may be empty)
         * @param spec        builder customizer for the child; may be {@code null}
         * @return this builder
         * @throws NullPointerException     if {@code name} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank after trimming
         */
        public Builder subcommand(String name, String description, Consumer<Builder> spec) {
            Builder b = new Builder(name).description(description);
            if (spec != null) spec.accept(b);
            this.children.add(b.build());
            return this;
        }

        /**
         * Sets the one-line description for the node being built.
         *
         * @param description node description; {@code null} becomes empty
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description == null ? "" : description.trim();
            return this;
        }

        /**
         * Builds an immutable {@link UsageNode} snapshot from the builder state.
         *
         * <p>All lists are defensively copied into immutable lists. String fields are trimmed.
         *
         * @return the constructed {@link UsageNode}
         */
        public UsageNode build() {
            return new UsageNode(
                    name,
                    description,
                    syntaxes,
                    parameters,
                    options,
                    examples,
                    notes,
                    children
            );
        }
    }

}
