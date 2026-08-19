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
package top.chiloven.lukosbot2.core;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.core.model.message.media.*;
import top.chiloven.lukosbot2.platform.PlatformFileLoader;

import java.io.IOException;
import java.util.List;

@Component
public class MediaRefLoader {

    private final List<PlatformFileLoader> platformFileLoaders;
    private final IUrlMediaLoader urlMediaLoader;

    public MediaRefLoader(
            List<PlatformFileLoader> platformFileLoaders,
            IUrlMediaLoader urlMediaLoader
    ) {
        this.platformFileLoaders = platformFileLoaders;
        this.urlMediaLoader = urlMediaLoader;
    }

    public LoadedPlatformMedia load(MediaRef ref) throws IOException {
        return switch (ref) {
            case BytesRef(String name, byte[] bytes, String mime) -> new LoadedPlatformMedia(
                    bytes,
                    name,
                    mime
            );
            case UrlRef urlRef -> urlMediaLoader.load(urlRef);
            case PlatformFileRef platformFileRef -> loadPlatform(platformFileRef);
            case null -> throw new IOException("不支持的媒体类型，无法读取。");
        };
    }

    private LoadedPlatformMedia loadPlatform(PlatformFileRef ref) throws IOException {
        return platformFileLoaders.stream()
                .filter(it -> it.supports(ref.platform()))
                .findFirst()
                .orElseThrow(() -> new IOException("当前平台不支持读取该媒体。"))
                .load(ref);
    }

}
