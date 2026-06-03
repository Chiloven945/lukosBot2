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

internal object DownloadDefaults {

    const val DEFAULT_MAX_CONCURRENT_FILES: Int = 8
    const val DEFAULT_CHUNK_THREADS: Int = 4
    const val DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES: Long = 8L * 1024 * 1024
    const val DEFAULT_MIN_PART_SIZE_BYTES: Long = 2L * 1024 * 1024
    const val DEFAULT_MAX_RETRIES: Int = 3
    const val DEFAULT_RETRY_BASE_DELAY_MS: Long = 350
    const val DEFAULT_RETRY_MAX_DELAY_MS: Long = 8_000
    const val DEFAULT_RETRY_AFTER_CAP_MS: Long = 30_000
    const val DEFAULT_PROGRESS_LOG_INTERVAL_MS: Long = 1_000
    const val BUFFER_SIZE: Int = 64 * 1024
    const val INVALID_BATCH_NAME: String = "file"

}
