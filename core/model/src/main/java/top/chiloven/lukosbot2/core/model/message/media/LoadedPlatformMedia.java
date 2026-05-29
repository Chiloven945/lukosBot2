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

/**
 * Fully loaded binary media ready to be sent to a platform SDK.
 *
 * <p>This DTO lives in core-model so platform-api implementations can return
 * loaded media without depending on core-runtime.</p>
 */
public record LoadedPlatformMedia(
        byte[] bytes,
        String name,
        String mime
) {

}
