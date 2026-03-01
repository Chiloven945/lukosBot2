package top.chiloven.lukosbot2.util.message

import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.model.message.Address
import top.chiloven.lukosbot2.model.message.inbound.*
import top.chiloven.lukosbot2.model.message.media.BytesRef
import top.chiloven.lukosbot2.model.message.media.MediaRef
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.model.message.media.UrlRef
import top.chiloven.lukosbot2.model.message.outbound.*

object MessageIoLog {

    private val log = LogManager.getLogger(MessageIoLog::class.java)
    private const val MAX_TEXT = 160

    @JvmStatic
    fun inbound(`in`: InboundMessage?) {
        if (!log.isDebugEnabled || `in` == null) return
        log.info("[IN ] {}", briefInbound(`in`))
    }

    @JvmStatic
    fun outbound(out: OutboundMessage?) {
        if (!log.isDebugEnabled || out == null) return
        log.info("[OUT] {}", briefOutbound(out))
    }

    private fun briefInbound(`in`: InboundMessage): String {
        val addr = briefAddr(`in`.addr())
        val who = briefSender(`in`.sender())
        val text = clip(TextExtractor.allText(`in`))
        val parts = briefInParts(`in`.partsSafe())

        val body = text.ifBlank { "<no text>" }
        return addr + " <- " + who + " | " + body + if (parts.isEmpty()) "" else " | $parts"
    }

    private fun briefOutbound(out: OutboundMessage): String {
        val addr = briefAddr(out.addr())
        val parts = briefOutParts(out.parts())
        return addr + " -> " + parts.ifEmpty { "<empty>" }
    }

    private fun briefAddr(a: Address?): String {
        if (a == null) return "unknown"
        return a.platform().name + ":" + (if (a.group()) "g" else "p") + ":" + a.chatId()
    }

    private fun briefSender(s: Sender?): String {
        if (s == null) return "unknown"
        var name = s.displayName()
        if (name.isNullOrBlank()) name = s.username()
        if (name.isNullOrBlank()) name = s.id()?.toString() ?: "unknown"
        return name + if (s.bot()) " (bot)" else ""
    }

    private fun clip(s: String?): String {
        if (s == null) return ""
        val t = s.replace("\r", "").replace("\n", "\\n").trim()
        return if (t.length <= MAX_TEXT) t else t.substring(0, MAX_TEXT) + "..."
    }

    private fun briefInParts(parts: List<InPart>?): String {
        if (parts.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.isNotEmpty()) sb.append(", ")
            when (p) {
                is InText -> sb.append("text(").append(lenSafe(p.text())).append(")")
                is InImage -> sb.append("image(")
                    .append(briefMedia(p.ref()))
                    .append(capBrief(p.caption()))
                    .append(")")

                is InFile -> sb.append("file(")
                    .append(nameOr("? ", p.name()))
                    .append(briefMedia(p.ref()))
                    .append(sizeBrief(p.size()))
                    .append(capBrief(p.caption()))
                    .append(")")
            }
        }
        return sb.toString()
    }

    private fun briefOutParts(parts: List<OutPart>?): String {
        if (parts.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.isNotEmpty()) sb.append(" | ")
            when (p) {
                is OutText -> sb.append("text:").append(clip(p.text()))
                is OutImage -> sb.append("image(")
                    .append(briefMedia(p.ref()))
                    .append(capBrief(p.caption()))
                    .append(")")

                is OutFile -> sb.append("file(")
                    .append(nameOr("? ", p.name()))
                    .append(briefMedia(p.ref()))
                    .append(capBrief(p.caption()))
                    .append(")")
            }
        }
        return sb.toString()
    }

    private fun lenSafe(s: String?): Int = s?.length ?: 0

    private fun briefMedia(ref: MediaRef?): String {
        if (ref == null) return "ref=null"
        return when (ref) {
            is UrlRef -> "url=" + clip(ref.url())
            is PlatformFileRef -> "pf=" + ref.platform() + ":" + clip(ref.fileId())
            is BytesRef -> "bytes" + (ref.name()?.let { ":$it" } ?: "")
        }
    }

    private fun capBrief(cap: String?): String {
        val c = clip(cap)
        return if (c.isBlank()) "" else ", cap=$c"
    }

    private fun nameOr(prefix: String, name: String?): String {
        if (name.isNullOrBlank()) return prefix
        return "$prefix$name, "
    }

    private fun sizeBrief(size: Long?): String {
        if (size == null || size <= 0) return ""
        return ", size=$size"
    }

}
