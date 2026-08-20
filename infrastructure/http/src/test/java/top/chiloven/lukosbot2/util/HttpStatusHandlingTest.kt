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
package top.chiloven.lukosbot2.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpStatusHandlingTest {

    @Test
    fun `HttpStatusException parses Retry-After seconds at exception boundary`() {
        assertEquals(
            2_000L,
            HttpStatusException.fromStatus(429, retryAfterHeader = "2").retryAfterMs
        )
    }

    @Test
    fun `HttpStatusException parses Retry-After RFC 1123 date`() {
        assertNotNull(
            HttpStatusException.fromStatus(
                429,
                retryAfterHeader = "Wed, 21 Oct 2099 07:28:00 GMT"
            ).retryAfterMs
        )
    }

    @Test
    fun `HttpStatusException exposes retryable flag correctly`() {
        assertTrue(HttpStatusException.isRetryableStatus(429))
        assertTrue(HttpStatusException.isRetryableStatus(500))
        assertTrue(HttpStatusException.isRetryableStatus(503))
        assertTrue(!HttpStatusException.isRetryableStatus(400))
        assertTrue(!HttpStatusException.isRetryableStatus(404))
    }

    @Test
    fun `HttpStatusException friendly message mapping works`() {
        val e404 = HttpStatusException.fromStatus(404)
        assertTrue(HttpStatusException.message(e404).contains("资源不存在"))

        val e429 = HttpStatusException.fromStatus(429)
        assertTrue(HttpStatusException.message(e429).contains("请求过于频繁"))
    }

}
