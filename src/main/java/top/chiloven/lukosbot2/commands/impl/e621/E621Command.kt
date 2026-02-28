package top.chiloven.lukosbot2.commands.impl.e621

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.impl.e621.schema.Artist
import top.chiloven.lukosbot2.commands.impl.e621.schema.Post
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.StringUtils.isUrl
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["e621"],
    havingValue = "true",
    matchIfMissing = true
)
class E621Command : IBotCommand {
    private val log = LogManager.getLogger(E621Command::class.java)

    override fun name(): String = "e621"

    override fun description(): String = "从 E621 获取信息"

    override fun usage(): UsageNode = UsageNode.root(name())
        .description(description())
        .syntax(
            "获取信息",
            UsageNode.lit("get"),
            UsageNode.oneOf(
                UsageNode.lit("artist"),
                UsageNode.lit("post"),
            ),
            UsageNode.oneOf(
                UsageNode.arg("id"),
                UsageNode.arg("link"),
            )
        )
        .syntax(
            "搜索信息",
            UsageNode.lit("search"),
            UsageNode.oneOf(
                UsageNode.lit("artist"),
                UsageNode.lit("post"),
            ),
            UsageNode.arg("text"),
            UsageNode.opt(UsageNode.arg("page"))
        )
        .syntax(
            "通过 MD5 搜索 post 信息",
            UsageNode.lit("search"),
            UsageNode.lit("md5"),
            UsageNode.arg("md5")
        )
        .param("id", "E621 上 artist 或 post 的 ID")
        .param("link", "E621 的链接")
        .param("text", "需要被搜索的文本")
        .param("page", "搜索结果页数（从 1 开始，默认 1）")
        .param("md5", "图片的 MD5")
        .example(
            "e621 get artist 123456",
            "e621 search artist ABC",
            "e621 search artist ABC 2",
            "e621 search post cat 3",
            "e621 search md5 9f6e6800cfae7749eb6c486619254b9c"
        )
        .build()

    // TODO: Rating E on group limitation. Limit viewing post with E rating on NSFW content not-allowed chat.

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    literal("get")
                        .then(
                            literal("artist")
                                .then(
                                    argument("artist", StringArgumentType.string())
                                        .executes { ctx ->
                                            val input = StringArgumentType.getString(ctx, "artist")!!
                                            val id = ctx.source.extractIdOrReply(input) ?: return@executes 0

                                            ctx.source.reply(
                                                Artist.fromJsonObject(E621Api.getArtistsXIdOrName(id)).getString()
                                            )
                                            1
                                        }
                                ))
                        .then(
                            literal("post")
                                .then(
                                    argument("post", StringArgumentType.string())
                                        .executes { ctx ->
                                            val input = StringArgumentType.getString(ctx, "post")!!
                                            val id = ctx.source.extractIdOrReply(input) ?: return@executes 0

                                            ctx.source.reply(
                                                Post.fromJsonObject(E621Api.getPostsXId(id)).getString()
                                            )
                                            1
                                        }
                                ))
                )
                .then(
                    literal("search")
                        .then(
                            literal("artist")
                                .then(
                                    argument("text", StringArgumentType.string())
                                        .executes { ctx ->
                                            val search = StringArgumentType.getString(ctx, "text")
                                            ctx.source.reply(
                                                ctx.source.search(SearchType.ARTIST, search) ?: return@executes 0
                                            )
                                            1
                                        }
                                        .then(
                                            argument("page", IntegerArgumentType.integer(1))
                                                .executes { ctx ->
                                                    val search = StringArgumentType.getString(ctx, "text")
                                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                                    ctx.source.reply(
                                                        ctx.source.search(SearchType.ARTIST, search, page)
                                                            ?: return@executes 0
                                                    )
                                                    1
                                                }
                                        )
                                )
                        )
                        .then(
                            literal("post")
                                .then(
                                    argument("text", StringArgumentType.string())
                                        .executes { ctx ->
                                            val search = StringArgumentType.getString(ctx, "text")
                                            ctx.source.reply(
                                                ctx.source.search(SearchType.POST, search) ?: return@executes 0
                                            )
                                            1
                                        }
                                        .then(
                                            argument("page", IntegerArgumentType.integer(1))
                                                .executes { ctx ->
                                                    val search = StringArgumentType.getString(ctx, "text")
                                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                                    ctx.source.reply(
                                                        ctx.source.search(SearchType.POST, search, page)
                                                            ?: return@executes 0
                                                    )
                                                    1
                                                }
                                        )
                                )
                        )
                        .then(
                            literal("md5")
                                .then(
                                    argument("md5", StringArgumentType.string())
                                        .executes { ctx ->
                                            val md5Raw = StringArgumentType.getString(ctx, "md5").trim()
                                            val md5 = md5Raw.lowercase()

                                            if (!Regex("^[0-9a-fA-F]{32}$").matches(md5)) {
                                                ctx.source.reply("MD5 格式不正确：$md5Raw（应为 32 位十六进制）")
                                                return@executes 0
                                            }

                                            val posts = Post.fromJsonArray(
                                                E621Api.getPosts(
                                                    limit = 1,
                                                    page = 1,
                                                    md5 = md5
                                                )
                                            )

                                            if (posts.isEmpty()) {
                                                ctx.source.reply("未找到 MD5 为 $md5 的帖子。")
                                                return@executes 0
                                            }

                                            ctx.source.reply(posts.first().getString())
                                            1
                                        }
                                )
                        )
                )
        )
    }

    private fun CommandSource.extractIdOrReply(input: String): String? {
        if (!input.isUrl()) return input
        return Regex("/(?:artists|posts)/(\\d+)").find(input)?.groupValues?.get(1).also {
            if (it == null) reply("输入的 URL 有误，请检查你的 URL。")
        }
    }

    enum class SearchType { ARTIST, POST }

    private fun CommandSource.search(
        type: SearchType,
        search: String,
        page: Int = 1,
        limit: Int = 12
    ): String? {
        val briefs: List<String> = when (type) {
            SearchType.ARTIST -> {
                val artists = Artist.fromJsonArray(
                    E621Api.getArtists(
                        limit = limit,
                        page = page,
                        searchAnyNameMatches = search
                    )
                )
                artists.map { it.getStringBrief() }
            }

            SearchType.POST -> {
                val posts = Post.fromJsonArray(
                    E621Api.getPosts(limit = limit, page = page, tags = search)
                )

                log.debug("[e621] Search post tags='{}' page={} limit={} results={}", search, page, limit, posts.size)

                if (posts.isNotEmpty()) {
                    try {
                        val png = SearchGridRenderer.render(search, page, posts)
                        log.debug("[e621] Grid rendered bytes={}", png.size)
                        sendImagePng("e621-posts-$page.png", png)
                        log.debug("[e621] Grid sent")
                    } catch (e: Exception) {
                        log.warn("[e621] Grid render/send failed.", e)
                    }
                } else {
                    log.debug("[e621] Skip grid render: no posts.")
                }

                posts.map { it.getStringBrief() }
            }
        }

        if (briefs.isEmpty()) {
            reply("未搜索到匹配 $search 的结果，或指定的页数过大。")
            return null
        }

        val start = (page - 1) * limit + 1
        val end = start + briefs.size - 1

        return buildString {
            appendLine("“$search” 的搜索结果：")
            appendLine()
            briefs.forEach { appendLine(it) }
            appendLine()
            appendLine("正在显示第 $start 至 $end 条搜索结果。")
            if (page == 1) {
                append("在命令后加入页码以查看指定页数。")
            }
        }
    }

}
