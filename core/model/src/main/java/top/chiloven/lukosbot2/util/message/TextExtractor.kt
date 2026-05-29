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

import top.chiloven.lukosbot2.core.model.message.inbound.*

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
