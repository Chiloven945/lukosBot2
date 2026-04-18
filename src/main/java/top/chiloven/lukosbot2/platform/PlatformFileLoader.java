package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.core.MediaRefLoader;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;

import java.io.IOException;

public interface PlatformFileLoader {

    boolean supports(String platform);

    MediaRefLoader.LoadedPlatformMedia load(PlatformFileRef ref) throws IOException;

}
