package top.chiloven.lukosbot2.commands.impl.music

import top.chiloven.lukosbot2.util.StringUtils.fmtTime

data class TrackInfo(
    val platform: MusicPlatform,
    val id: String,
    val name: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val url: String? = null,
    val durationMs: Long = 0L
) {
    fun formatted(): String = buildString {
        append("平台：").append(platform.displayName).append('\n')
        append("标题：").append(name).append('\n')
        append("艺术家：").append(artist).append('\n')

        album?.takeIf { it.isNotBlank() }?.let {
            append("专辑：").append(it).append('\n')
        }
        if (durationMs > 0) {
            append("时长：").append(durationMs.fmtTime("mm:ss")).append('\n')
        }
        url?.takeIf { it.isNotBlank() }?.let {
            append("链接：").append(it).append('\n')
        }
        coverUrl?.takeIf { it.isNotBlank() }?.let {
            append("封面：").append(it).append('\n')
        }
    }
}
