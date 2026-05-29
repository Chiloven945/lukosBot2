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
package top.chiloven.lukosbot2.commands.bot.bilibili.schema

sealed class VideoId {

    abstract fun normalized(): String

    data class Bv(
        val bvid: String
    ) : VideoId() {

        override fun normalized(): String = bvid

    }

    data class Av(
        val aid: Long
    ) : VideoId() {

        override fun normalized(): String = "av$aid"

    }

    companion object {

        private val bvPattern = Regex("""(?i)\bBV([0-9A-Za-z]{10})\b""")
        private val avPattern = Regex("""(?i)\bAV?(\d+)\b""")

        fun parse(input: String): VideoId? {
            bvPattern.find(input)?.groupValues?.getOrNull(1)?.let { return Bv("BV$it") }
            avPattern.find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return Av(it) }
            return null
        }

    }

}
