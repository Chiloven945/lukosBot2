/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.platform.discord

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.utils.FileUpload
import top.chiloven.lukosbot2.core.model.message.media.BytesRef
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.core.model.message.media.UrlRef
import top.chiloven.lukosbot2.core.model.message.outbound.*
import top.chiloven.lukosbot2.platform.ISender
import top.chiloven.lukosbot2.util.message.OutboundPartUtils
import java.io.ByteArrayInputStream

/**
 * Discord sender that translates [OutboundMessage] into Discord API calls.
 *
 * <p>Discord does not have a true "caption" concept for images like Telegram does. To preserve
 * the ordering semantics of {@link OutboundMessage#parts()}, this sender sends parts sequentially: each
 * [OutText], [OutImage], [OutFile] is sent as one Discord message (or a small sequence if the text is
 * too long).</p>
 */
internal class DiscordSender(
    private val stack: DiscordStack,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ISender {

    private companion object {

        const val MAX_CONTENT = 2000

    }

    override suspend fun send(out: OutboundMessage) {
        withContext(blockingDispatcher) {
            val parts = out.parts().orEmpty()
            if (parts.isEmpty()) return@withContext

            val normalized = OutboundPartUtils.mergeAdjacentTextParts(parts)

            if (out.addr().group()) {
                val ch = stack.jda.getTextChannelById(out.addr().chatId())
                    ?: return@withContext
                sendParts(ch, normalized)
                return@withContext
            }

            val userId = out.addr().chatId()
            try {
                val u = stack.jda.retrieveUserById(userId).complete()
                    ?: return@withContext
                val pc = u.openPrivateChannel().complete()
                    ?: return@withContext
                sendParts(pc, normalized)
            } catch (_: Exception) {
                // ignore errors opening DMs
            }
        }
    }

    private fun sendParts(ch: MessageChannel?, parts: List<OutPart>) {
        if (ch == null || parts.isEmpty()) return

        parts.forEach { p ->
            when (p) {
                is OutText -> sendTextChunks(ch, OutboundPartUtils.safeText(p.text()))
                is OutImage -> sendImagePart(ch, p)
                is OutFile -> sendFilePart(ch, p)
            }
        }
    }

    private fun sendTextChunks(ch: MessageChannel, text: String) {
        if (text.isBlank()) return

        // Discord content limit: 2000 chars. Split into chunks.
        var i = 0
        while (i < text.length) {
            val end = minOf(text.length, i + MAX_CONTENT)
            val chunk = text.substring(i, end)
            ch.sendMessage(chunk).complete()
            i = end
        }
    }

    private fun sendImagePart(ch: MessageChannel, img: OutImage?) {
        if (img?.ref() == null) return

        val caption = OutboundPartUtils.safeText(img.caption())
        when (val ref = img.ref()) {
            is BytesRef -> {
                val upload = FileUpload.fromData(
                    ByteArrayInputStream(ref.bytes()),
                    OutboundPartUtils.pickMediaName(
                        img.name(),
                        ref.name(),
                        img.mime(),
                        true
                    ),
                )
                if (caption.isBlank()) {
                    ch.sendFiles(upload).complete()
                } else {
                    sendWithOptionalUpload(
                        ch,
                        caption,
                        listOf(upload),
                        emptyList()
                    )
                }
            }

            is UrlRef -> {
                val embed = EmbedBuilder().setImage(ref.url()).build()
                sendWithOptionalUpload(ch, caption, emptyList(), listOf(embed))
            }

            is PlatformFileRef -> {
                // Not directly usable on Discord; fall back to showing the identifier.
                val msg = if (caption.isBlank()) {
                    "[image ref] " + ref.platform() + ":" + ref.fileId()
                } else {
                    caption + "\n[image ref] " + ref.platform() + ":" + ref.fileId()
                }
                sendTextChunks(ch, msg)
            }
        }
    }

    private fun sendFilePart(ch: MessageChannel, f: OutFile?) {
        if (f?.ref() == null) return

        val caption = OutboundPartUtils.safeText(f.caption())
        when (val ref = f.ref()) {
            is BytesRef -> {
                val upload = FileUpload.fromData(
                    ByteArrayInputStream(ref.bytes()),
                    OutboundPartUtils.pickMediaName(
                        f.name(),
                        ref.name(),
                        f.mime(),
                        false
                    ),
                )
                if (caption.isBlank()) {
                    ch.sendFiles(upload).complete()
                } else {
                    sendWithOptionalUpload(
                        ch,
                        caption,
                        listOf(upload),
                        emptyList()
                    )
                }
            }

            is UrlRef -> {
                val msg = if (caption.isBlank()) ref.url() else "$caption\n${ref.url()}"
                sendTextChunks(ch, msg)
            }

            is PlatformFileRef -> {
                val msg = if (caption.isBlank()) {
                    "[file ref] ${ref.platform()}:${ref.fileId()}"
                } else {
                    "$caption\n[file ref] ${ref.platform()}:${ref.fileId()}"
                }
                sendTextChunks(ch, msg)
            }
        }
    }

    private fun sendWithOptionalUpload(
        ch: MessageChannel,
        content: String,
        uploads: List<FileUpload>,
        embeds: List<MessageEmbed>,
    ) {
        var c = OutboundPartUtils.safeText(content)

        // If content is too long, send content first (chunked), then embeds/files.
        if (c.length > MAX_CONTENT) {
            sendTextChunks(ch, c)
            c = ""
        }

        val hasUploads = uploads.isNotEmpty()
        val hasEmbeds = embeds.isNotEmpty()

        if (!hasUploads && !hasEmbeds) {
            if (c.isNotBlank()) ch.sendMessage(c).complete()
            return
        }

        if (hasUploads) {
            val action = ch.sendFiles(uploads)
            @Suppress("UsePropertyAccessSyntax")
            action.setContent(c.ifBlank { "" })
            if (hasEmbeds) {
                action.setEmbeds(embeds)
            }
            action.complete()
            return
        }

        // embeds only
        ch.sendMessage(c.ifBlank { "" }).setEmbeds(embeds).complete()
    }

}
