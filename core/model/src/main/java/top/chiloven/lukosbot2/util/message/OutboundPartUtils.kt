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
package top.chiloven.lukosbot2.util.message

import top.chiloven.lukosbot2.core.model.message.outbound.OutPart
import top.chiloven.lukosbot2.core.model.message.outbound.OutText
import java.util.*

/**
 * Shared helpers for outbound message-part normalization across platform senders.
 */
object OutboundPartUtils {

    @JvmStatic
    fun safeText(s: String?): String = s ?: ""

    @JvmStatic
    fun mergeAdjacentTextParts(parts: List<OutPart?>?): List<OutPart> {
        if (parts.isNullOrEmpty()) return emptyList()

        val out = mutableListOf<OutPart>()
        val sb = StringBuilder()

        for (part in parts) {
            when (part) {
                is OutText -> {
                    val normalized = safeText(part.text)
                    if (normalized.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(normalized)
                    }
                }

                null -> flushText(sb, out)
                else -> {
                    flushText(sb, out)
                    out += part
                }
            }
        }

        flushText(sb, out)
        return out
    }

    @JvmStatic
    fun pickMediaName(
        preferred: String?,
        fallback: String?,
        mime: String?,
        imageDefault: Boolean,
    ): String {
        if (!preferred.isNullOrBlank()) return preferred
        if (!fallback.isNullOrBlank()) return fallback

        return if (mime?.lowercase(Locale.ROOT)?.contains("image") == true || imageDefault) {
            "image.bin"
        } else {
            "file.bin"
        }
    }

    private fun flushText(sb: StringBuilder, out: MutableList<OutPart>) {
        if (sb.isEmpty()) return
        out += OutText(sb.toString())
        sb.setLength(0)
    }

}
