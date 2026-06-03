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

import top.chiloven.lukosbot2.util.PathUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal object DownloadNaming {

    fun flatFileName(name: String?): String =
        PathUtils.sanitizeFileName(name, DownloadDefaults.INVALID_BATCH_NAME)

    @Throws(IOException::class)
    fun resolveFlatTarget(dir: Path, fileName: String): Path {
        Files.createDirectories(dir)
        return dir.resolve(flatFileName(fileName))
    }

    @Throws(IOException::class)
    fun normalizeRelativeEntryName(entryName: String): String =
        PathUtils.normalizeRelativeEntryName(entryName)

    @Throws(IOException::class)
    fun uniqueRelativeEntryName(
        entryName: String,
        usedNames: MutableSet<String>,
    ): String = PathUtils.uniqueRelativeEntryName(entryName, usedNames)

    @Throws(IOException::class)
    fun resolveRelativeTarget(baseDir: Path, entryName: String): Path {
        val safe = normalizeRelativeEntryName(entryName)
        val base = baseDir.toAbsolutePath().normalize()
        val target = base.resolve(safe).normalize()
        if (!target.startsWith(base)) {
            throw IOException("Illegal target path: $entryName")
        }
        target.parent?.let(Files::createDirectories)
        return target
    }

}
