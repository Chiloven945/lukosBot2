package top.chiloven.lukosbot2.commands.impl.music.provider

import top.chiloven.lukosbot2.commands.impl.music.MusicPlatform
import top.chiloven.lukosbot2.commands.impl.music.TrackInfo
import java.net.http.HttpClient

interface IMusicProvider {
    /**
     * Return the music platform
     * 
     * @return the corresponding MusicPlatform
     */
    fun platform(): MusicPlatform

    /**
     * Search track by query
     * 
     * @param query search query
     * @return TrackInfo
     * @throws Exception when remote API call fails
     */
    @Throws(Exception::class)
    fun searchTrack(query: String): TrackInfo?

    /**
     * Resolve track info from link, like:
     * 
     *  * https://open.spotify.com/track/...
     *  * https://soundcloud.com/...
     * 
     * 
     * @param link music link
     * @return TrackInfo
     * @throws Exception when remote API call fails
     */
    @Throws(Exception::class)
    fun resolveLink(link: String): TrackInfo?

    companion object {
        /**
         * HTTP client instance
         */
        @JvmField
        val HTTP: HttpClient = HttpClient.newHttpClient()
    }
}
