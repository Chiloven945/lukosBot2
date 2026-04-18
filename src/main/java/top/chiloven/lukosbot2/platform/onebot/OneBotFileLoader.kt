package top.chiloven.lukosbot2.platform.onebot

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.core.MediaRefLoader.LoadedPlatformMedia
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import top.chiloven.lukosbot2.util.HttpBytes
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@Component
class OneBotFileLoader : PlatformFileLoader {

    override fun supports(platform: String): Boolean = platform.equals("onebot", ignoreCase = true)

    @Throws(IOException::class)
    override fun load(ref: PlatformFileRef): LoadedPlatformMedia {
        val raw = ref.fileId().trim()
        return when {
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> {
                val remote = HttpBytes.get(raw)
                LoadedPlatformMedia(remote.bytes, remote.fileName, remote.mime)
            }

            raw.startsWith("file://", ignoreCase = true) -> readLocal(Path.of(URI.create(raw)))

            else -> {
                val maybeLocal = Path.of(raw)
                if (maybeLocal.isAbsolute && Files.exists(maybeLocal)) {
                    readLocal(maybeLocal)
                } else {
                    throw IOException("OneBot 图片引用无法直接读取：$raw")
                }
            }
        }
    }

    private fun readLocal(path: Path): LoadedPlatformMedia {
        val bytes = Files.readAllBytes(path)
        val name = path.fileName?.toString()
        val mime = Files.probeContentType(path)
        return LoadedPlatformMedia(bytes, name, mime)
    }

}
