package top.chiloven.lukosbot2.commands.impl.music;

import top.chiloven.lukosbot2.util.StringUtils;

/**
 * The track information
 *
 * @param id         the ID of the track
 * @param name       the name of the track
 * @param artist     the artist of the track
 * @param album      the album of the track
 * @param coverUrl   the cover URL of the track
 * @param durationMs the duration of the track in milliseconds
 */
public record TrackInfo(
        MusicPlatform platform,
        String id,
        String name,
        String artist,
        String album,
        String coverUrl,
        String url,
        long durationMs
) {
    public static final StringUtils su = StringUtils.getStringUtils();

    /**
     * Format track info for display
     *
     * @return formatted track info
     */
    public String formatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("平台：").append(platform.getName()).append('\n');
        sb.append("标题：").append(name).append('\n');
        sb.append("艺术家：").append(artist).append('\n');
        if (album != null && !album.isBlank()) {
            sb.append("专辑：").append(album).append('\n');
        }
        if (durationMs > 0) {
            sb.append("时长：").append(su.formatTime(durationMs, "mm:ss")).append('\n');
        }
        if (url != null && !url.isBlank()) {
            sb.append("链接：").append(url).append('\n');
        }
        if (coverUrl != null && !coverUrl.isBlank()) {
            sb.append("封面：").append(coverUrl).append('\n');
        }
        return sb.toString();
    }
}
