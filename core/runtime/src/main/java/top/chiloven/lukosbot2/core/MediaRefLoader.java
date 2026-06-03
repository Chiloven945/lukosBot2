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
import top.chiloven.lukosbot2.util.HttpBytes;

import java.io.IOException;
import java.util.List;

@Component
public class MediaRefLoader {

    private final List<PlatformFileLoader> platformFileLoaders;

    public MediaRefLoader(List<PlatformFileLoader> platformFileLoaders) {
        this.platformFileLoaders = platformFileLoaders;
    }

    public LoadedPlatformMedia load(MediaRef ref) throws IOException {
        if (ref instanceof BytesRef(String name, byte[] bytes, String mime)) {
            return new LoadedPlatformMedia(bytes, name, mime);
        }
        if (ref instanceof UrlRef(String url)) {
            var remote = HttpBytes.getResponse(url).getBody();
            return new LoadedPlatformMedia(remote.getBytes(), remote.getFileName(), remote.getMime());
        }
        if (ref instanceof PlatformFileRef platformFileRef) {
            return loadPlatform(platformFileRef);
        }
        throw new IOException("不支持的媒体类型，无法读取。");
    }

    private LoadedPlatformMedia loadPlatform(PlatformFileRef ref) throws IOException {
        return platformFileLoaders.stream()
                .filter(it -> it.supports(ref.platform()))
                .findFirst()
                .orElseThrow(() -> new IOException("当前平台不支持读取该媒体。"))
                .load(ref);
    }

}
