package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.core.model.message.media.LoadedPlatformMedia;
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef;

import java.io.IOException;

public interface PlatformFileLoader {

    boolean supports(String platform);

    LoadedPlatformMedia load(PlatformFileRef ref) throws IOException;

}
