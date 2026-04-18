package top.chiloven.lukosbot2.commands.impl.cave

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.MediaRefLoader
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.core.state.Scope
import top.chiloven.lukosbot2.core.state.store.IStateStore
import top.chiloven.lukosbot2.model.message.inbound.InFile
import top.chiloven.lukosbot2.model.message.inbound.InImage
import top.chiloven.lukosbot2.model.message.inbound.InPart
import top.chiloven.lukosbot2.model.message.inbound.InText
import top.chiloven.lukosbot2.model.message.media.BytesRef
import top.chiloven.lukosbot2.model.message.outbound.OutImage
import top.chiloven.lukosbot2.model.message.outbound.OutPart
import top.chiloven.lukosbot2.model.message.outbound.OutText
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.util.StringUtils
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class CaveService(
    private val store: IStateStore,
    private val mapper: JsonMapper,
    private val mediaRefLoader: MediaRefLoader,
    appProperties: AppProperties
) {

    companion object {

        private const val NS_META = "cmd.cave.meta"
        private const val NS_ENTRY = "cmd.cave.entry"
        private const val KEY_META = "meta"
        private val GLOBAL_SCOPE: Scope = Scope.global()

    }

    private val prefix: String = appProperties.prefix?.ifBlank { "/" } ?: "/"
    private val lock = ReentrantLock()

    fun get(no: Int): CaveEntry? = readEntry(no)

    fun random(): CaveEntry? {
        val meta = readMeta()
        if (meta.activeNumbers.isEmpty()) {
            return rebuildMetaFromEntries().activeNumbers.randomOrNull()?.let(::readEntry)
        }

        val numbers = meta.activeNumbers.shuffled()
        for (no in numbers) {
            val entry = readEntry(no)
            if (entry != null) return entry
        }

        val rebuilt = rebuildMetaFromEntries()
        return rebuilt.activeNumbers.randomOrNull()?.let(::readEntry)
    }

    fun add(src: CommandSource): CaveEntry {
        val payload = extractPayload(src)
            ?: throw IllegalArgumentException("未检测到可保存的文本或图片。请直接发送内容、使用 /cave add <message>，或回复一条带文本/图片的消息后使用 /cave add。")

        return lock.withLock {
            val meta = readMeta()
            val no = meta.nextNo.coerceAtLeast(1)
            val usedQuoted = src.quoted()?.let { hasSupportedContent(it.partsSafe()) } == true
            val sourceMessageId = if (usedQuoted) src.quoted()?.messageId() else src.meta().messageId()

            val entry = CaveEntry(
                no = no,
                uuid = UUID.randomUUID().toString(),
                text = payload.text,
                image = payload.image,
                createdAt = System.currentTimeMillis(),
                createdByPlatform = src.platform().name,
                createdByUserId = src.userIdOrNull(),
                createdByChat = src.addr().toString(),
                sourceMessageId = sourceMessageId
            )

            writeEntry(entry)
            val active = (meta.activeNumbers + no).distinct().sorted()
            writeMeta(CaveMeta(nextNo = no + 1, activeNumbers = active))
            entry
        }
    }

    fun delete(no: Int): Boolean = lock.withLock {
        val exists = readEntry(no) != null
        if (!exists) return false
        store.delete(GLOBAL_SCOPE, NS_ENTRY, no.toString())
        val meta = readMeta()
        val active = meta.activeNumbers.filterNot { it == no }
        writeMeta(CaveMeta(nextNo = meta.nextNo.coerceAtLeast(no + 1), activeNumbers = active))
        true
    }

    fun toOutbound(src: CommandSource, entry: CaveEntry, includeMeta: Boolean = false): OutboundMessage {
        val parts = mutableListOf<OutPart>()
        val metaLine = if (includeMeta) buildMetaLine(entry) else null
        val textLine = entry.text?.takeIf { it.isNotBlank() }

        entry.image?.let {
            val bytes = Base64.getDecoder().decode(it.base64)
            val caption = listOfNotNull(metaLine, textLine)
                .joinToString("\n")
                .ifBlank { null }

            parts += OutImage(
                BytesRef(it.name, bytes, it.mime),
                caption,
                it.name,
                it.mime
            )
            return OutboundMessage(src.addr(), parts)
        }

        listOfNotNull(metaLine, textLine).forEach { parts += OutText(it) }

        if (parts.isEmpty()) {
            parts += OutText("该条目为空。")
        }
        return OutboundMessage(src.addr(), parts)
    }

    private fun buildMetaLine(entry: CaveEntry): String {
        val createdAt = StringUtils.formatTime(entry.createdAt) ?: "-"
        return "#${entry.no} - $createdAt"
    }

    private fun extractPayload(src: CommandSource): CavePayload? {
        src.quoted()?.let { quoted ->
            val payload = payloadFromParts(quoted.partsSafe())
            if (payload != null) return payload
        }

        return payloadFromCurrentParts(src.parts())
    }

    private fun payloadFromCurrentParts(parts: List<InPart>): CavePayload? {
        if (parts.isEmpty()) return null
        val text = extractCurrentText(parts)
        val image = extractFirstImage(parts)
        return if (text == null && image == null) null else CavePayload(text, image)
    }

    private fun payloadFromParts(parts: List<InPart>): CavePayload? {
        if (parts.isEmpty()) return null

        val text = extractVisibleText(parts)
        val image = extractFirstImage(parts)

        return if (text == null && image == null) null else CavePayload(text, image)
    }

    private fun extractCurrentText(parts: List<InPart>): String? {
        if (parts.isEmpty()) return null
        val prefixRegex = Regex(
            "^\\s*${Regex.escape(prefix)}(?:cave|c)\\s+add\\b",
            setOf(RegexOption.IGNORE_CASE)
        )

        var commandStripped = false
        val chunks = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                is InText -> {
                    var value = part.text()?.trim().orEmpty()
                    if (!commandStripped) {
                        val replaced = value.replaceFirst(prefixRegex, "").trim()
                        commandStripped = replaced != value
                        value = replaced
                    }
                    if (value.isNotBlank()) chunks += value
                }

                is InImage -> {
                    val caption = part.caption()?.trim().orEmpty()
                    if (caption.isNotBlank()) {
                        var value = caption
                        if (!commandStripped) {
                            val replaced = value.replaceFirst(prefixRegex, "").trim()
                            commandStripped = replaced != value
                            value = replaced
                        }
                        if (value.isNotBlank()) chunks += value
                    }
                }

                is InFile -> {
                    val caption = part.caption()?.trim().orEmpty()
                    if (caption.isNotBlank()) {
                        var value = caption
                        if (!commandStripped) {
                            val replaced = value.replaceFirst(prefixRegex, "").trim()
                            commandStripped = replaced != value
                            value = replaced
                        }
                        if (value.isNotBlank()) chunks += value
                    }
                }
            }
        }
        return chunks.joinToString("\n").ifBlank { null }
    }

    @Throws(IOException::class)
    private fun extractFirstImage(parts: List<InPart>): CaveImageBlob? {
        for (part in parts) {
            if (part is InImage) {
                return normalizeImage(part)
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun normalizeImage(image: InImage): CaveImageBlob {
        val source = image.ref() ?: throw IOException("图片引用为空")
        val loaded = mediaRefLoader.load(source)
        val name = image.name()?.takeIf { it.isNotBlank() } ?: loaded.name()
        val mime = image.mime()?.takeIf { it.isNotBlank() } ?: loaded.mime()
        return CaveImageBlob(name, mime, Base64.getEncoder().encodeToString(loaded.bytes()))
    }

    private fun extractVisibleText(parts: List<InPart>): String? {
        val chunks = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                is InText -> part.text()?.trim()?.takeIf { it.isNotBlank() }?.let(chunks::add)
                is InImage -> part.caption()?.trim()?.takeIf { it.isNotBlank() }?.let(chunks::add)
                is InFile -> part.caption()?.trim()?.takeIf { it.isNotBlank() }?.let(chunks::add)
            }
        }
        return chunks.joinToString("\n").ifBlank { null }
    }

    private fun hasSupportedContent(parts: List<InPart>): Boolean {
        if (parts.isEmpty()) return false
        return parts.any { part ->
            when (part) {
                is InText -> !part.text().isNullOrBlank()
                is InImage -> true
                else -> false
            }
        }
    }

    private fun readMeta(): CaveMeta {
        val json = store.getJson(GLOBAL_SCOPE, NS_META, KEY_META).orElse(null) ?: return CaveMeta()
        return try {
            mapper.readValue(json, CaveMeta::class.java)
        } catch (_: Exception) {
            CaveMeta()
        }
    }

    private fun writeMeta(meta: CaveMeta) {
        store.upsertJson(GLOBAL_SCOPE, NS_META, KEY_META, mapper.writeValueAsString(meta), null)
    }

    private fun readEntry(no: Int): CaveEntry? {
        val json = store.getJson(GLOBAL_SCOPE, NS_ENTRY, no.toString()).orElse(null) ?: return null
        return try {
            mapper.readValue(json, CaveEntry::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeEntry(entry: CaveEntry) {
        store.upsertJson(GLOBAL_SCOPE, NS_ENTRY, entry.no.toString(), mapper.writeValueAsString(entry), null)
    }

    private fun rebuildMetaFromEntries(): CaveMeta = lock.withLock {
        val keys = store.getNamespaceJson(GLOBAL_SCOPE, NS_ENTRY)
            .keys
            .mapNotNull { it.toIntOrNull() }
            .sorted()
        val meta = CaveMeta(
            nextNo = ((keys.maxOrNull() ?: 0) + 1).coerceAtLeast(1),
            activeNumbers = keys
        )
        writeMeta(meta)
        meta
    }

    private data class CavePayload(
        val text: String?,
        val image: CaveImageBlob?
    )

    data class CaveEntry(
        val no: Int,
        val uuid: String,
        val text: String?,
        val image: CaveImageBlob?,
        val createdAt: Long,
        val createdByPlatform: String,
        val createdByUserId: Long?,
        val createdByChat: String?,
        val sourceMessageId: String?
    )

    data class CaveImageBlob(
        val name: String?,
        val mime: String?,
        val base64: String
    )

    data class CaveMeta(
        val nextNo: Int = 1,
        val activeNumbers: List<Int> = emptyList()
    )

}
