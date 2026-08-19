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
package top.chiloven.lukosbot2.platform.telegram

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import top.chiloven.lukosbot2.core.model.message.media.BytesRef
import top.chiloven.lukosbot2.core.model.message.media.MediaRef
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.core.model.message.media.UrlRef
import top.chiloven.lukosbot2.core.model.message.outbound.OutFile
import top.chiloven.lukosbot2.core.model.message.outbound.OutImage
import top.chiloven.lukosbot2.core.model.message.outbound.OutText
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.platform.ISender
import top.chiloven.lukosbot2.util.message.OutboundPartUtils
import java.io.ByteArrayInputStream

/**
 * Telegram sender that translates [OutboundMessage] into Telegram API calls.
 *
 * <p>Supports ordered, mixed content via {@link OutboundMessage#parts()}:
 * text, image and file. When {@code DeliveryHints.preferCaption()} is enabled, the sender will try to merge an adjacent
 * text part into a media caption for a better user experience.</p>
 */
internal class TelegramSender(
    private val stack: TelegramStack,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ISender {

    override suspend fun send(out: OutboundMessage) {
        withContext(blockingDispatcher) {
            val chatId = out.addr().chatId().toString()
            val parts = out.parts().orEmpty()
            if (parts.isEmpty()) return@withContext

            val normalized = OutboundPartUtils.mergeAdjacentTextParts(parts)

            val preferCaption = out.hints() != null && out.hints().preferCaption()

            var i = 0
            while (i < normalized.size) {
                when (val p = normalized[i]) {
                    is OutText -> {
                        val text = OutboundPartUtils.safeText(p.text())
                        if (text.isBlank()) {
                            i++
                            continue
                        }

                        // If next part is media and prefers caption, try to attach this text as caption.
                        if (preferCaption && i + 1 < normalized.size) {
                            val next = normalized[i + 1]
                            if (next is OutImage && (next.caption().isNullOrBlank())) {
                                sendPhoto(chatId, next, text)
                                i += 2 // consume next
                                continue
                            }

                            if (next is OutFile && next.caption().isNullOrBlank()) {
                                sendDocument(chatId, next, text)
                                i += 2 // consume next
                                continue
                            }
                        }

                        sendText(chatId, text)
                    }

                    is OutImage -> sendPhoto(
                        chatId,
                        p,
                        OutboundPartUtils.safeText(p.caption())
                    )
                    is OutFile -> sendDocument(
                        chatId,
                        p,
                        OutboundPartUtils.safeText(p.caption())
                    )
                }
                i++
            }
        }
    }

    private fun sendPhoto(
        chatId: String,
        img: OutImage?,
        caption: String
    ) {
        if (img == null) return
        val sp = SendPhoto.builder()
                .chatId(chatId)
                .photo(toInputFile(img.ref(), img.name(), img.mime()))
                .caption(caption.ifBlank { null })
                .build()
        stack.execute(sp)
    }

    private fun sendDocument(
        chatId: String,
        f: OutFile?,
        caption: String
    ) {
        if (f == null) return
        val sd = SendDocument.builder()
                .chatId(chatId)
                .document(toInputFile(f.ref(), f.name(), f.mime()))
                .caption(caption.ifBlank { null })
                .build()
        stack.execute(sd)
    }

    private fun sendText(chatId: String, text: String) {
        if (text.isBlank()) return
        val sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build()
        stack.execute(sm)
    }

    private fun toInputFile(
        ref: MediaRef?,
        name: String?,
        mime: String?
    ): InputFile {
        return when (ref) {
            null -> InputFile("about:blank")
            is BytesRef -> {
                val n = OutboundPartUtils.pickMediaName(
                    name,
                    ref.name(),
                    mime,
                    true
                )
                InputFile(ByteArrayInputStream(ref.bytes()), n)
            }

            is UrlRef -> InputFile(ref.url())
            is PlatformFileRef -> {
                // Telegram accepts file_id in the same field.
                InputFile(ref.fileId())
            }
        }
    }

}
