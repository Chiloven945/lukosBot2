package top.chiloven.lukosbot2.commands

/**
 * Render [UsageNode] into markdown text (for chat) or structured lines (for image rendering).
 *
 * This renderer intentionally keeps output compact and high-signal:
 * command title, short summary, concise "快速使用", merged "参数与选项", examples, tips, and subcommands.
 */
object UsageTextRenderer {

    @JvmStatic
    fun renderMarkdown(node: UsageNode?, options: Options): String =
        render(node, options).markdownText()

    @JvmStatic
    fun render(node: UsageNode?, options: Options): Result {
        if (node == null) return Result("", emptyList())

        val lines = ArrayList<RenderedLine>()

        if (options.includeHeader) {
            val cmd = joinInvocation(options.prefix, listOf(node.name), "")
            lines.add(RenderedLine.text(LineKind.TITLE, cmd))

            if (options.includeDescriptionInHeader && !node.description.isNullOrBlank()) {
                lines.add(RenderedLine.text(LineKind.SUBTITLE, node.description))
            }

            val aliases = node.aliases
            if (!aliases.isNullOrEmpty()) {
                lines.add(RenderedLine.text(LineKind.SUBTITLE, "别名： " + aliases.joinToString(" · ")))
            }

            lines.add(RenderedLine.blank())
        }

        if (options.includeUsageSection) {
            val all = ArrayList<SyntaxEntry>()
            collectSyntax(node, listOf(node.name), 0, options.maxDepth, all)

            lines.add(RenderedLine.text(LineKind.HEADING, "快速使用"))
            val seenInvocations = LinkedHashSet<String>()

            if (all.isEmpty()) {
                val inv = joinInvocation(options.prefix, listOf(node.name), "")
                lines.add(codeLine(inv, options))
            } else {
                for (syntax in all) {
                    val inv = joinInvocation(options.prefix, syntax.path, syntax.tail)
                    if (!seenInvocations.add(inv)) continue
                    lines.add(codeLine(inv, options))
                    if (!syntax.desc.isNullOrBlank()) {
                        lines.add(RenderedLine.text(LineKind.TEXT, syntax.desc.trim()))
                    }
                }
            }
            lines.add(RenderedLine.blank())
        }

        if (options.includeParametersSection || options.includeOptionsSection) {
            val withDocs = collectNodes(
                node = node,
                path = listOf(node.name),
                depth = 0,
                maxDepth = options.maxDepth
            ) { n ->
                (options.includeParametersSection && n.parameters.isNotEmpty()) ||
                        (options.includeOptionsSection && n.options.isNotEmpty())
            }

            if (withDocs.isNotEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "参数与选项"))
                val showPathLabel = withDocs.size > 1 || withDocs.any { it.path.size > 1 }

                for (np in withDocs) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix, np.path, "")))
                    }

                    if (options.includeParametersSection) {
                        for (p in np.node.parameters) {
                            val token = UsageNode.renderItem(p.token())
                            lines.add(keyValueBullet(token, p.description(), options))
                        }
                    }

                    if (options.includeOptionsSection) {
                        for (o in np.node.options) {
                            val token = UsageNode.renderItem(o.token())
                            lines.add(keyValueBullet(token, o.description(), options))
                        }
                    }
                }
                lines.add(RenderedLine.blank())
            }
        }

        if (options.includeExamplesSection) {
            val withEx = collectNodes(node, listOf(node.name), 0, options.maxDepth) { it.examples.isNotEmpty() }
            if (withEx.isNotEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "示例"))
                val showPathLabel = withEx.size > 1 || withEx.any { it.path.size > 1 }

                for (np in withEx) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix, np.path, "")))
                    }
                    for (ex in np.node.examples) {
                        lines.add(codeLine(normalizeExample(ex, options), options))
                    }
                }
                lines.add(RenderedLine.blank())
            }
        }

        if (options.includeNotesSection) {
            val withNotes = collectNodes(node, listOf(node.name), 0, options.maxDepth) { it.notes.isNotEmpty() }
            if (withNotes.isNotEmpty()) {
                lines.add(RenderedLine.text(LineKind.HEADING, "提示"))
                val showPathLabel = withNotes.size > 1 || withNotes.any { it.path.size > 1 }

                for (np in withNotes) {
                    if (showPathLabel) {
                        lines.add(RenderedLine.text(LineKind.LABEL, joinInvocation(options.prefix, np.path, "")))
                    }
                    for (note in np.node.notes) {
                        lines.add(RenderedLine.text(LineKind.BULLET, note))
                    }
                }
                lines.add(RenderedLine.blank())
            }
        }

        if (options.includeSubcommandsSection && node.children.isNotEmpty()) {
            lines.add(RenderedLine.text(LineKind.HEADING, "子命令"))
            for (child in node.children) {
                val inv = joinInvocation(options.prefix, listOf(node.name, child.name), "")
                val desc = child.description?.trim().orEmpty()
                val plain = if (desc.isBlank()) inv else "$inv — $desc"
                val mdInv = if (options.markdownBackticks) "`$inv`" else inv
                val md = if (desc.isBlank()) mdInv else "$mdInv — $desc"
                lines.add(RenderedLine(LineKind.BULLET, "• $md", "• $plain"))
            }
            lines.add(RenderedLine.blank())
        }

        while (lines.isNotEmpty() && lines.last().kind() == LineKind.BLANK) {
            lines.removeAt(lines.lastIndex)
        }

        val md = buildString {
            for (line in lines) {
                append(line.markdown()).append('\n')
            }
        }.trim()

        return Result(md, lines.toList())
    }

    private fun collectSyntax(
        node: UsageNode?,
        path: List<String>,
        depth: Int,
        maxDepth: Int,
        out: MutableList<SyntaxEntry>
    ) {
        if (node == null || depth > maxDepth) return

        for (syntax in node.syntaxes) {
            out.add(SyntaxEntry(path, syntax.tailText(), syntax.description()))
        }

        for (child in node.children) {
            collectSyntax(child, path + child.name, depth + 1, maxDepth, out)
        }
    }

    private fun collectNodes(
        node: UsageNode?,
        path: List<String>,
        depth: Int,
        maxDepth: Int,
        predicate: (UsageNode) -> Boolean
    ): List<NodeAtPath> {
        val out = ArrayList<NodeAtPath>()
        collectNodes0(node, path, depth, maxDepth, out, predicate)
        return out
    }

    private fun collectNodes0(
        node: UsageNode?,
        path: List<String>,
        depth: Int,
        maxDepth: Int,
        out: MutableList<NodeAtPath>,
        predicate: (UsageNode) -> Boolean
    ) {
        if (node == null || depth > maxDepth) return

        if (predicate(node)) {
            out.add(NodeAtPath(path, node))
        }

        for (child in node.children) {
            collectNodes0(child, path + child.name, depth + 1, maxDepth, out, predicate)
        }
    }

    private fun codeLine(invocation: String?, options: Options): RenderedLine {
        val plain = invocation?.trim().orEmpty()
        val md = if (options.markdownBackticks) "`$plain`" else plain
        return RenderedLine(LineKind.CODE, md, plain)
    }

    private fun keyValueBullet(key: String?, value: String?, options: Options): RenderedLine {
        val k = key?.trim().orEmpty()
        val v = value?.trim().orEmpty()

        val plain = if (v.isBlank()) k else "$k — $v"
        val mdKey = if (options.markdownBackticks) "`$k`" else k
        val md = if (v.isBlank()) mdKey else "$mdKey — $v"
        return RenderedLine(LineKind.BULLET, "• $md", "• $plain")
    }

    private fun joinInvocation(prefix: String?, path: List<String>, tail: String?): String {
        val p = prefix.orEmpty()
        val core = path.joinToString(" ")
        var inv = p + core
        if (!tail.isNullOrBlank()) {
            inv += " " + tail.trim()
        }
        return inv.trim()
    }

    private fun normalizeExample(example: String?, options: Options): String {
        if (example == null) return ""
        var e = example.trim()
        if (!options.autoPrefixExamples) return e

        val p = options.prefix
        if (p.isBlank()) return e
        if (e.startsWith(p)) return e
        if (p == "/" && e.startsWith("/")) return e
        if (e.startsWith(" ")) e = e.trim()

        return p + e
    }

    enum class LineKind {
        TITLE,
        SUBTITLE,
        HEADING,
        LABEL,
        CODE,
        TEXT,
        BULLET,
        BLANK
    }

    data class RenderedLine(
        val kind: LineKind,
        val markdown: String,
        val plain: String
    ) {

        fun kind(): LineKind = kind
        fun markdown(): String = markdown
        fun plain(): String = plain

        companion object {

            @JvmStatic
            fun blank(): RenderedLine = RenderedLine(LineKind.BLANK, "", "")

            @JvmStatic
            fun text(kind: LineKind, text: String?): RenderedLine {
                val t = text.orEmpty()
                return RenderedLine(kind, t, t)
            }

        }

    }

    data class Result(
        val markdownText: String,
        val lines: List<RenderedLine>
    ) {

        fun markdownText(): String = markdownText
        fun lines(): List<RenderedLine> = lines

    }

    data class Options(
        val prefix: String,
        val markdownBackticks: Boolean,
        val includeHeader: Boolean,
        val includeDescriptionInHeader: Boolean,
        val includeUsageSection: Boolean,
        val includeParametersSection: Boolean,
        val includeOptionsSection: Boolean,
        val includeExamplesSection: Boolean,
        val includeNotesSection: Boolean,
        val includeSubcommandsSection: Boolean,
        val maxDepth: Int,
        val autoPrefixExamples: Boolean
    ) {

        companion object {

            @JvmStatic
            fun forHelp(prefix: String): Options = Options(
                prefix = prefix,
                markdownBackticks = true,
                includeHeader = true,
                includeDescriptionInHeader = true,
                includeUsageSection = true,
                includeParametersSection = true,
                includeOptionsSection = true,
                includeExamplesSection = true,
                includeNotesSection = true,
                includeSubcommandsSection = true,
                maxDepth = 8,
                autoPrefixExamples = true
            )

            /**
             * Usage text returned inside a command itself (usually shorter, no header).
             */
            @JvmStatic
            fun forCommand(prefix: String): Options = Options(
                prefix = prefix,
                markdownBackticks = true,
                includeHeader = false,
                includeDescriptionInHeader = false,
                includeUsageSection = true,
                includeParametersSection = true,
                includeOptionsSection = true,
                includeExamplesSection = true,
                includeNotesSection = true,
                includeSubcommandsSection = true,
                maxDepth = 6,
                autoPrefixExamples = true
            )

        }

    }

    private data class SyntaxEntry(
        val path: List<String>,
        val tail: String?,
        val desc: String?
    )

    private data class NodeAtPath(
        val path: List<String>,
        val node: UsageNode
    )

}
