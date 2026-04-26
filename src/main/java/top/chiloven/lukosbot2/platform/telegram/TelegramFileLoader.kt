package top.chiloven.lukosbot2.platform.telegram

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.MediaRefLoader.LoadedPlatformMedia
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import top.chiloven.lukosbot2.util.HttpBytes
import top.chiloven.lukosbot2.util.HttpJson
import java.io.IOException
import java.net.URI

@Component
class TelegramFileLoader(
    private val appProperties: AppProperties
) : PlatformFileLoader {

    override fun supports(platform: String): Boolean = platform.equals("telegram", ignoreCase = true)

    @Throws(IOException::class)
    override fun load(ref: PlatformFileRef): LoadedPlatformMedia {
        val token = appProperties.telegram.botToken.trim()
        if (token.isBlank()) {
            throw IOException("Telegram 配置不完整，无法读取图片。")
        }

        val root = HttpJson.getObject(
            URI("https://api.telegram.org/bot$token/getFile"),
            mapOf("file_id" to ref.fileId())
        )
        val filePath = root.path("result").path("file_path").asString(null)
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Telegram 未返回文件路径。")

        val remote = HttpBytes.get("https://api.telegram.org/file/bot$token/$filePath")
        return LoadedPlatformMedia(
            remote.bytes,
            remote.fileName ?: filePath.substringAfterLast('/').ifBlank { null },
            remote.mime
        )
    }

}
