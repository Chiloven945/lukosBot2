package top.chiloven.lukosbot2.commands.impl.e621

import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.MAPPER
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.obj
import java.net.URI

object E621Api {

    val API: URI = URI("https://e621.net")

    fun getArtists(
        limit: Int? = null,
        page: Int? = null,
        searchId: Int? = null,
        searchOrder: String? = null,
        searchName: String? = null,
        searchGroupName: String? = null,
        searchAnyOtherNameLike: String? = null,
        searchAnyNameMatches: String? = null,
        searchAnyNameOrUrlMatches: String? = null,
        searchUrlMatches: String? = null,
        searchCreatorName: String? = null,
        searchCreatorId: String? = null,
        searchHasTag: String? = null,
        searchIsLinked: String? = null,
        searchLinkedUserId: Int? = null,
        searchLinkedUserName: String? = null
    ): ArrayNode {
        val params = mapOf(
            "limit" to limit,
            "page" to page,
            "search[id]" to searchId,
            "search[order]" to searchOrder,
            "search[name]" to searchName,
            "search[group_name]" to searchGroupName,
            "search[any_other_name_like]" to searchAnyOtherNameLike,
            "search[any_name_matches]" to searchAnyNameMatches,
            "search[any_name_or_url_matches]" to searchAnyNameOrUrlMatches,
            "search[url_matches]" to searchUrlMatches,
            "search[creator_name]" to searchCreatorName,
            "search[creator_id]" to searchCreatorId,
            "search[has_tag]" to searchHasTag,
            "search[is_linked]" to searchIsLinked,
            "search[linked_user_id]" to searchLinkedUserId,
            "search[linked_user_name]" to searchLinkedUserName
        ).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

        return HttpJson.getArray(API.resolve("artists.json"), params)
    }

    fun getArtistsXIdOrName(idOrName: String): ObjectNode =
        HttpJson.getObject(API.resolve("artists/$idOrName.json"))

    fun getPosts(
        limit: Int? = null,
        page: Int? = null,
        tags: String? = null,
        md5: String? = null,
        random: String? = null,
    ): ArrayNode {
        val params = mapOf(
            "limit" to limit,
            "page" to page,
            "tags" to tags,
            "md5" to md5,
            "random" to random
        ).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

        val root = HttpJson.getObject(API.resolve("posts.json"), params)
        root.arr("posts")?.let { return it }
        root.obj("post")?.let { postObj ->
            return MAPPER.createArrayNode().add(postObj)
        }
        return MAPPER.createArrayNode()
    }

    fun getPostsXRandom(tags: String? = null): ObjectNode =
        HttpJson.getObject(
            API.resolve("posts/random.json"),
            mapOf("tags" to tags).filterValues { it != null }
        ).obj("post")!!

    fun getPostsXId(id: String): ObjectNode =
        HttpJson.getObject(API.resolve("posts/$id.json")).obj("post")!!

}
