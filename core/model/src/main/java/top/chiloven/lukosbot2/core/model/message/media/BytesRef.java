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
package top.chiloven.lukosbot2.core.model.message.media;

import java.util.Arrays;

/**
 * Media held in memory.
 */
public record BytesRef(
        String name,
        byte[] bytes,
        String mime
) implements MediaRef {

    public BytesRef(byte[] bytes) {
        this(null, bytes, null);
    }

    public BytesRef {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        // defensive copy
        bytes = Arrays.copyOf(bytes, bytes.length);
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

}
