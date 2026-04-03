package top.chiloven.lukosbot2.commands.impl.kemono

import org.apache.logging.log4j.LogManager
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.commands.impl.kemono.schema.Service
import top.chiloven.lukosbot2.util.HttpJson
import java.io.IOException
import java.net.URI

object KemonoAPI {

    private val log = LogManager.getLogger(KemonoAPI::class.java)

    private val API = URI.create("https://kemono.cr/api/")

    /** Kemono only accept "text/css"*/
    private val HEADER = mapOf("Accept" to "text/css")

    private fun resolve(path: String): URI {
        val uri: URI = API.resolve(path)
        log.debug("Kemono API request: {}", uri)
        return uri
    }

    @Throws(IOException::class)
    fun getSpecificPost(
        service: Service,
        creatorId: String,
        postId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/user/$creatorId/post/$postId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorProfile(
        service: Service,
        creatorId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/user/$creatorId/profile"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorLinks(
        service: Service,
        creatorId: String
    ): ArrayNode = HttpJson.getArray(
        resolve("v1/$service/user/$creatorId/links"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorPosts(
        service: Service,
        creatorId: String
    ): ArrayNode = HttpJson.getArray(
        resolve("v1/$service/user/$creatorId/posts"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getDiscordChannelPost(channelId: String): ArrayNode = HttpJson.getArray(
        resolve("v1/discord/channel/$channelId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getDiscordServerChannel(channelId: String): ArrayNode = HttpJson.getArray(
        resolve("v1/discord/channel/lookup/$channelId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getFileFromHash(hash: String): ObjectNode = HttpJson.getObject(
        resolve("v1/search_hash/$hash"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getPostFromServicePost(
        service: Service,
        servicePostId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/post/$servicePostId"),
        headers = HEADER
    )

}
