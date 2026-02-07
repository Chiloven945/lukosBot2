package top.chiloven.lukosbot2.commands.music.provider;

import top.chiloven.lukosbot2.commands.music.MusicPlatform;
import top.chiloven.lukosbot2.commands.music.TrackInfo;

import java.net.http.HttpClient;

public interface IMusicProvider {

    /**
     * HTTP client instance
     */
    HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Return the music platform
     *
     * @return the corresponding MusicPlatform
     */
    MusicPlatform platform();

    /**
     * Search track by query
     *
     * @param query search query
     * @return TrackInfo
     * @throws Exception when remote API call fails
     */
    TrackInfo searchTrack(String query) throws Exception;

    /**
     * Resolve track info from link, like:
     * <ul>
     *     <li>https://open.spotify.com/track/...</li>
     *     <li>https://soundcloud.com/...</li>
     * </ul>
     *
     * @param link music link
     * @return TrackInfo
     * @throws Exception when remote API call fails
     */
    TrackInfo resolveLink(String link) throws Exception;
}
