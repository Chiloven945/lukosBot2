package top.chiloven.lukosbot2.core;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.model.message.media.BytesRef;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
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
            var remote = HttpBytes.get(url);
            return new LoadedPlatformMedia(remote.getBytes(), remote.getFileName(), remote.getMime());
        }
        if (ref instanceof PlatformFileRef platformFileRef) {
            return loadPlatform(platformFileRef);
        }
        throw new IOException("不支持的媒体引用类型：" + ref.getClass().getSimpleName());
    }

    private LoadedPlatformMedia loadPlatform(PlatformFileRef ref) throws IOException {
        return platformFileLoaders.stream()
                .filter(it -> it.supports(ref.platform()))
                .findFirst()
                .orElseThrow(() -> new IOException("不支持的平台媒体引用：" + ref.platform() + ":" + ref.fileId()))
                .load(ref);
    }

    public record LoadedPlatformMedia(
            byte[] bytes,
            String name,
            String mime
    ) {

    }

}
