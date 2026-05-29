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
package top.chiloven.lukosbot2.core.model.message.inbound;

import top.chiloven.lukosbot2.core.model.message.media.MediaRef;

/**
 * Image segment.
 *
 * @param ref     media reference (url, bytes, or platform file id)
 * @param caption optional caption
 * @param name    optional file name
 * @param mime    optional mime type
 */
public record InImage(
        MediaRef ref,
        String caption,
        String name,
        String mime
) implements InPart {

    public InImage {
        if (caption != null && caption.isBlank()) caption = null;
        if (name != null && name.isBlank()) name = null;
        if (mime != null && mime.isBlank()) mime = null;
    }

    @Override
    public InPartType type() {
        return InPartType.IMAGE;
    }

}
