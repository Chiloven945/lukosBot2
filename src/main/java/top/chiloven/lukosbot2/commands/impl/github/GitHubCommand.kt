package top.chiloven.lukosbot2.commands.impl.github

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.impl.github.data.GitHubRepo
import top.chiloven.lukosbot2.commands.impl.github.data.GitHubSearchResult
import top.chiloven.lukosbot2.commands.impl.github.data.GitHubUser
import top.chiloven.lukosbot2.commands.impl.github.data.SearchParams
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["github"],
    havingValue = "true",
    matchIfMissing = true
)
class GitHubCommand(ccp: CommandConfigProp) : IBotCommand {

    private val log = LogManager.getLogger(GitHubCommand::class.java)
    private val api = GitHubApi(ccp.gitHub.token)

    override fun name(): String = "github"
    override fun description(): String = "GitHub 查询工具"

    override fun usage(): UsageNode {
        return UsageNode.root(name())
            .description(description())
            .subcommand("user", "查询用户信息") { b ->
                b.syntax("查询用户信息", UsageNode.arg("username"))
                    .param("username", "GitHub 用户名")
                    .example("github user GitHub")
            }
            .subcommand("repo", "查询仓库信息") { b ->
                b.syntax("查询仓库信息", UsageNode.arg("owner/repo"))
                    .param("owner/repo", "仓库名，格式 owner/repo")
                    .example("github repo Chiloven945/lukosbot2")
            }
            .subcommand("search", "搜索仓库") { b ->
                val top = UsageNode.concat(UsageNode.lit("--top="), UsageNode.arg("num"))
                val lang = UsageNode.concat(UsageNode.lit("--lang="), UsageNode.arg("lang"))
                val sort = UsageNode.concat(
                    UsageNode.lit("--sort="),
                    UsageNode.oneOf(UsageNode.lit("stars"), UsageNode.lit("updated"))
                )
                val order = UsageNode.concat(
                    UsageNode.lit("--order="),
                    UsageNode.oneOf(UsageNode.lit("desc"), UsageNode.lit("asc"))
                )

                b.syntax(
                    "搜索仓库",
                    UsageNode.arg("keyword"),
                    UsageNode.opt(top),
                    UsageNode.opt(lang),
                    UsageNode.opt(sort),
                    UsageNode.opt(order),
                )
                    .param("keyword", "搜索关键字")
                    .option(top, "返回数量（示例：--top=5）")
                    .option(lang, "语言过滤（示例：--lang=java）")
                    .option(sort, "排序字段（stars 或 updated）")
                    .option(order, "排序方向（desc 或 asc）")
                    .example("github search lukosbot --top=5 --lang=java --sort=stars --order=desc")
            }
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .then(
                    literal("user")
                        .then(
                            argument("username", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val username = StringArgumentType.getString(ctx, "username")
                                    ctx.source.reply(handleUser(username))
                                    1
                                })
                )
                .then(
                    literal("repo")
                        .then(
                            argument("repo", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val repoArg = StringArgumentType.getString(ctx, "repo")
                                    ctx.source.reply(handleRepo(repoArg))
                                    1
                                })
                )
                .then(
                    literal("search")
                        .then(
                            argument("query", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val query = StringArgumentType.getString(ctx, "query")
                                    ctx.source.reply(handleSearch(query))
                                    1
                                })
                )
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
        )
    }

    private fun handleUser(username: String): String {
        return runCatching {
            val obj = api.getUser(username)
            GitHubUser.from(obj).toReadableText()
        }.getOrElse { e ->
            log.warn("github user 查询失败: {}", username, e)
            "找不到用户或请求失败：$username"
        }
    }

    private fun handleRepo(repoArg: String): String {
        val (owner, repo) = repoArg.split("/", limit = 2).let { parts ->
            if (parts.size != 2) return "仓库格式应为 owner/repo"
            parts[0] to parts[1]
        }

        return runCatching {
            val obj = api.getRepo(owner, repo)
            GitHubRepo.from(obj).toReadableText()
        }.getOrElse { e ->
            log.warn("github repo 查询失败: {}", repoArg, e)
            "找不到仓库或请求失败：$repoArg"
        }
    }

    private fun handleSearch(input: String): String {
        return runCatching {
            val p = SearchParams.parse(input)
            val json = api.searchRepos(p.keywords, p.sort, p.order, p.language, p.top)
            GitHubSearchResult.from(json, top = p.top).toReadableText()
        }.getOrElse { e ->
            log.warn("github search 失败: {}", input, e)
            "搜索失败：${e.message ?: "unknown error"}"
        }
    }

}

