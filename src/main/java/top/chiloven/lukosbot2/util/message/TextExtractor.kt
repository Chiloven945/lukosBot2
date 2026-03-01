package top.chiloven.lukosbot2.util.message

import top.chiloven.lukosbot2.model.message.inbound.*

/**
 * Utilities for extracting text from rich inbound messages.
 */
object TextExtractor {

    /**
     * Extract primary text for command parsing.
     *
     * Order of preference:
     *  1) First non-blank InText
     *  2) First non-blank caption on InImage
     *  3) First non-blank caption on InFile
     */
    @JvmStatic
    fun primaryText(msg: InboundMessage?): String {
        if (msg == null) return ""
        val parts: List<InPart> = msg.parts() ?: return ""
        if (parts.isEmpty()) return ""

        // 1) InText first
        for (p in parts) {
            if (p is InText) {
                val s = safeTrim(p.text())
                if (s.isNotEmpty()) return s
            }
        }

        // 2) caption on InImage / InFile
        for (p in parts) {
            when (p) {
                is InImage -> {
                    val s = safeTrim(p.caption())
                    if (s.isNotEmpty()) return s
                }

                is InFile -> {
                    val s = safeTrim(p.caption())
                    if (s.isNotEmpty()) return s
                }

                else -> {}
            }
        }

        return ""
    }

    private fun safeTrim(s: String?): String = s?.trim().orEmpty()

    /**
     * Extract all visible text for NLP/LLM processing.
     */
    @JvmStatic
    fun allText(msg: InboundMessage?): String {
        if (msg == null) return ""
        val parts: List<InPart> = msg.parts() ?: return ""
        if (parts.isEmpty()) return ""

        val sb = StringBuilder()
        for (p in parts) {
            when (p) {
                is InText -> appendLine(sb, p.text())
                is InImage -> appendLine(sb, p.caption())
                is InFile -> appendLine(sb, p.caption())
            }
        }
        return sb.toString().trim()
    }

    private fun appendLine(sb: StringBuilder, s: String?) {
        val t = safeTrim(s)
        if (t.isEmpty()) return
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(t)
    }

}
