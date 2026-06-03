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
package top.chiloven.lukosbot2.util.download

import top.chiloven.lukosbot2.util.StringUtils

internal object DownloadFormatting {

    fun displayBytes(bytes: Long): String =
        if (bytes < 0) "?" else StringUtils.fmtBytes(bytes, 2)

    fun formatSpeed(bytes: Long, elapsedNs: Long): String {
        if (bytes < 0 || elapsedNs <= 0) return "?"
        val sec = elapsedNs / 1_000_000_000.0
        return displayBytes((bytes / sec).toLong()) + "/s"
    }

}
