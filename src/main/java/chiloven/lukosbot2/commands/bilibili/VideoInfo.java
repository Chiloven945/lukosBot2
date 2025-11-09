package chiloven.lukosbot2.commands.bilibili;

import lombok.Data;

/**
 * Bilibili video information
 */
@Data
public final class VideoInfo {
    private String bvid;
    private String title;
    private String tname;
    private String desc;
    private String cover;
    private long pubDateMs;

    private String ownerName;
    private long ownerMid;
    private long fans;

    private long view, danmaku, reply, favorite, coin, share, like;
    private int pageCount = 1;
}