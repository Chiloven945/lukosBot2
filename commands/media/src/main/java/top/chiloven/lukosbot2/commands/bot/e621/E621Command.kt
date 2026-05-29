/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.commands.bot.e621

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.bot.e621.schema.Artist
import top.chiloven.lukosbot2.commands.bot.e621.schema.Post
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.util.StringUtils.isUrl

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["e621"],
    havingValue = "true",
    matchIfMissing = true
)
class E621Command(
    private val policyService: PolicyService
) : IBotCommand {

    private val log = LogManager.getLogger(E621Command::class.java)

    private val commandDefinition = botCommand("e621") {
        description = "查询 E621 上的作品和作者信息"

        execute {
            this@E621Command.sendUsage(source)
        }

        literal("get") {
            literal("artist") {
                argv {
                    positional("id", ArgType.StringType) {
                        required = true
                    }
                    execute { args ->
                        getArtist(source, args.get("id"))
                    }
                }
            }
            literal("post") {
                argv {
                    positional("id", ArgType.StringType) {
                        required = true
                    }
                    execute { args ->
                        getPost(source, args.get("id"))
                    }
                }
            }
        }

        literal("search") {
            literal("artist") {
                argv {
                    positional("text", ArgType.StringType) {
                        required = true
                        greedy = true
                    }
                    positional("page", ArgType.IntType) {
                        required = false
                        default = 1
                    }
                    execute { args ->
                        searchArtist(source, args.get("text"), args.get("page"))
                    }
                }
            }
            literal("post") {
                argv {
                    positional("text", ArgType.StringType) {
                        required = true
                        greedy = true
                    }
                    positional("page", ArgType.IntType) {
                        required = false
                        default = 1
                    }
                    execute { args ->
                        searchPost(source, args.get("text"), args.get("page"))
                    }
                }
            }
            literal("md5") {
                argv {
                    positional("md5", ArgType.StringType) {
                        required = true
                    }
                    execute { args ->
                        searchMd5(source, args.get("md5"))
                    }
                }
            }
        }

        example(
            "e621 get artist 123456",
            "e621 search artist ABC",
            "e621 search post cat 3",
            "e621 search md5 9f6e6800cfae7749eb6c486619254b9c"
        )
    }

    override fun definition() = commandDefinition

    private fun getArtist(src: CommandSource, input: String) {
        val id = src.extractIdOrReply(input) ?: return
        src.reply(Artist.fromJsonObject(E621Api.getArtistsXIdOrName(id)).getString())
    }

    private fun getPost(src: CommandSource, input: String) {
        val id = src.extractIdOrReply(input) ?: return
        val post = Post.fromJsonObject(E621Api.getPostsXId(id))
        if (!src.isPostAllowed(post)) return
        src.reply(post.getString())
    }

    private fun searchArtist(src: CommandSource, search: String, page: Int) {
        src.reply(src.search(SearchType.ARTIST, search, page) ?: return)
    }

    private fun searchPost(src: CommandSource, search: String, page: Int) {
        src.reply(src.search(SearchType.POST, search, page) ?: return)
    }

    private fun searchMd5(src: CommandSource, md5Raw: String) {
        val md5 = md5Raw.trim().lowercase()

        if (!Regex("^[0-9a-fA-F]{32}$").matches(md5)) {
            src.reply("MD5 格式不正确：$md5Raw")
            return
        }

        val posts = Post.fromJsonArray(E621Api.getPosts(limit = 1, page = 1, md5 = md5))
        if (posts.isEmpty()) {
            src.reply("未找到 MD5 为 $md5 的帖子。")
            return
        }

        val post = posts.first()
        if (!src.isPostAllowed(post)) return

        src.reply(post.getString())
    }

    private fun CommandSource.extractIdOrReply(input: String): String? =
        if (!input.isUrl()) {
            input
        } else {
            Regex("/(?:artists|posts)/(\\d+)")
                .find(input)
                ?.groupValues
                ?.get(1)
                .also {
                    if (it == null) reply("无法从该链接中识别 ID。")
                }
        }

    private fun CommandSource.isPostAllowed(post: Post): Boolean {
        if (post.rating.normalizedRating() in allowedRatings()) return true
        reply("该内容分级为 ${post.rating.uppercase()}，当前聊天因策略限制不可查看。")
        return false
    }

    private fun CommandSource.allowedRatings(): Set<String> = policyService.allowedValues(
        this,
        POLICY_KEY_RATING,
        DEFAULT_ALLOWED_RATINGS
    )

    enum class SearchType {

        ARTIST,
        POST

    }

    private fun CommandSource.search(
        type: SearchType,
        search: String,
        page: Int,
        limit: Int = 12
    ): String? {
        val allowedRatings = allowedRatings().map { it.normalizedRating() }.toSet()
        val result = when (type) {
            SearchType.ARTIST -> {
                val artists = Artist.fromJsonArray(
                    E621Api.getArtists(
                        limit = limit,
                        page = page,
                        searchAnyNameMatches = search
                    )
                )
                SearchResult(artists.map { it.getStringBrief() })
            }

            SearchType.POST -> {
                val effectiveSearch = if ("e" in allowedRatings) {
                    search
                } else {
                    if (search.isBlank()) "-rating:e" else "$search -rating:e"
                }

                val posts = Post.fromJsonArray(
                    E621Api.getPosts(
                        limit = limit,
                        page = page,
                        tags = effectiveSearch
                    )
                )

                val visiblePosts = posts.filter { it.rating.normalizedRating() in allowedRatings }

                if (visiblePosts.isNotEmpty()) {
                    try {
                        this.reply(
                            OutboundMessage.imageBytesPng(
                                addr(),
                                SearchGridRenderer.render(search, page, visiblePosts),
                                "e621-posts-$page.png"
                            )
                        )
                    } catch (e: Exception) {
                        log.warn("[e621] Grid render/send failed.", e)
                    }
                }

                SearchResult(
                    visiblePosts.map { it.getStringBrief() },
                    filteredOnly = posts.isNotEmpty() && visiblePosts.isEmpty()
                )
            }
        }

        if (result.briefs.isEmpty()) {
            reply(if (result.filteredOnly) "搜索结果已被过滤。" else "未找到匹配的结果。")
            return null
        }

        val start = (page - 1) * limit + 1
        val end = start + result.briefs.size - 1
        return buildString {
            appendLine("\"$search\"的搜索结果：")
            appendLine()
            result.briefs.forEach {
                appendLine(it)
            }
            appendLine()
            appendLine("当前显示第 $start 至 $end 条结果。")
        }
    }

    private data class SearchResult(
        val briefs: List<String>,
        val filteredOnly: Boolean = false
    )

    companion object {

        private const val POLICY_KEY_RATING = "e621.rating"
        private val DEFAULT_ALLOWED_RATINGS = setOf("s", "q", "e")

    }

    private fun String.normalizedRating(): String = trim().lowercase()

}
