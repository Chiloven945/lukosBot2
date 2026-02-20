package top.chiloven.lukosbot2.commands.impl.e621

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.obj
import java.io.IOException
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
    ): JsonArray {
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
        ).mapNotNull { (key, value) ->
            value?.let { key to it.toString() }
        }.toMap()

        try {
            return HttpJson.getArray(
                API.resolve("artists.json"),
                params
            )
        } catch (e: IOException) {
            throw e
        }
    }

    fun getArtistsXIdOrName(
        idOrName: String
    ): JsonObject {
        return HttpJson.getObject(
            API.resolve("artists/$idOrName.json")
        )
    }

    fun getPosts(
        limit: Int? = null,
        page: Int? = null,
        tags: String? = null,
        md5: String? = null,
        random: String? = null,
    ): JsonArray {
        val params = mapOf(
            "limit" to limit,
            "page" to page,
            "tags" to tags,
            "md5" to md5,
            "random" to random
        ).mapNotNull { (key, value) ->
            value?.let { key to it.toString() }
        }.toMap()

        val root = HttpJson.getObject(
            API.resolve("posts.json"),
            params
        )

        // 1) normal list response: { "posts": [ ... ] }
        root.arr("posts")?.let { return it }

        // 2) single response: { "post": { ... } }
        root.obj("post")?.let { postObj ->
            return JsonArray().apply { add(postObj) }
        }

        // 3) unknown shape: return empty list
        return JsonArray()
    }

    fun getPostsXRandom(
        tags: String? = null
    ): JsonObject {
        return HttpJson.getObject(
            API.resolve("posts/random.json"),
            mapOf("tags" to tags).filterValues { it != null }
        ).obj("post")!!
    }

    fun getPostsXId(
        id: String
    ): JsonObject {
        return HttpJson.getObject(
            API.resolve("posts/$id.json")
        ).obj("post")!!
    }
}