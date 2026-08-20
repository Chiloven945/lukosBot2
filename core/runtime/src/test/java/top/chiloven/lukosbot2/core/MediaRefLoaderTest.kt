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
package top.chiloven.lukosbot2.core

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.core.model.message.media.BytesRef
import top.chiloven.lukosbot2.core.model.message.media.LoadedPlatformMedia
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.core.model.message.media.UrlRef
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import java.io.IOException

class MediaRefLoaderTest {

    private class StubUrlMediaLoader(
        private val result: LoadedPlatformMedia
    ) : IUrlMediaLoader {

        var loadedUrl: String? = null
            private set

        override suspend fun load(ref: UrlRef): LoadedPlatformMedia {
            loadedUrl = ref.url()
            return result
        }

    }

    private class StubPlatformFileLoader(
        private val supportedPlatform: String,
        private val result: LoadedPlatformMedia
    ) : PlatformFileLoader {

        override fun supports(platform: String): Boolean =
            platform.equals(supportedPlatform, ignoreCase = true)

        override suspend fun load(ref: PlatformFileRef): LoadedPlatformMedia = result

    }

    @Test
    fun `url ref delegates to url media loader`() = runTest {
        val remote = LoadedPlatformMedia(
            byteArrayOf(1, 2, 3),
            "a.png",
            "image/png"
        )
        val stub = StubUrlMediaLoader(remote)
        val loader = MediaRefLoader(listOf(), stub)

        val loaded = loader.load(UrlRef("https://example.com/a.png"))

        assertSame(remote, loaded)
        assertEquals("https://example.com/a.png", stub.loadedUrl)
    }

    @Test
    fun `bytes ref returns bytes directly`() = runTest {
        val loader = MediaRefLoader(
            listOf(),
            StubUrlMediaLoader(
                LoadedPlatformMedia(
                    byteArrayOf(0),
                    null,
                    null
                )
            )
        )

        val loaded = loader.load(
            BytesRef(
                "b.png",
                byteArrayOf(1, 2, 3),
                "image/png"
            )
        )

        assertEquals("b.png", loaded.name())
        assertEquals("image/png", loaded.mime())
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.bytes())
    }

    @Test
    fun `platform file ref delegates to matching platform file loader`() = runTest {
        val expected = LoadedPlatformMedia(
            byteArrayOf(9, 9),
            "c.png",
            "image/png"
        )
        val loader = MediaRefLoader(
            listOf(
                StubPlatformFileLoader(
                    "discord",
                    expected
                )
            ),
            StubUrlMediaLoader(
                LoadedPlatformMedia(
                    byteArrayOf(0),
                    null,
                    null
                )
            )
        )

        val loaded = loader.load(PlatformFileRef("discord", "file-1"))

        assertSame(expected, loaded)
    }

    @Test
    fun `platform file ref without matching loader throws`() = runTest {
        val loader = MediaRefLoader(
            listOf(
                StubPlatformFileLoader(
                    "discord",
                    LoadedPlatformMedia(
                        byteArrayOf(0),
                        null,
                        null
                    )
                )
            ),
            StubUrlMediaLoader(
                LoadedPlatformMedia(
                    byteArrayOf(0),
                    null,
                    null
                )
            )
        )

        kotlin.test.assertFailsWith<IOException> {
            loader.load(PlatformFileRef("telegram", "file-1"))
        }
    }

}
