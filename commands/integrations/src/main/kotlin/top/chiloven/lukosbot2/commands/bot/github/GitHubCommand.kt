package top.chiloven.lukosbot2.commands.bot.github

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubRepo
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubSearchResult
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubUser
import top.chiloven.lukosbot2.commands.bot.github.data.SearchParams
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.command.definition.parser.ArgvParseResult

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["github"],
    havingValue = "true",
    matchIfMissing = true
)
class GitHubCommand(
    ccp: CommandConfigProp
) : IBotCommand {

    private val log = LogManager.getLogger(GitHubCommand::class.java)
    private val api = GitHubApi(ccp.gitHub.token)

    private val commandDefinition = botCommand("github") {
        alias("gh")
        description = "GitHub 查询工具"

        execute {
            this@GitHubCommand.sendUsage(source)
        }

        literal("user") {
            description = "查询用户信息"

            raw("username") { username ->
                source.reply(handleUser(username))
            }

            param("username", "GitHub 用户名")
            example("github user GitHub")
        }

        literal("repo") {
            description = "查询仓库信息"

            raw("ownerRepo") { ownerRepo ->
                source.reply(handleRepo(ownerRepo))
            }

            param("owner/repo", "仓库名，格式 owner/repo")
            example("github repo Chiloven945/lukosbot2")
        }

        literal("search") {
            description = "搜索仓库"

            argv {
                positional("keyword", ArgType.StringType) {
                    required = true
                    greedy = true
                    description = "搜索关键字"
                }

                option("top") {
                    names = listOf("--top")
                    type = ArgType.IntType
                    default = 3
                    description = "返回数量"
                }

                option("lang") {
                    names = listOf("--lang")
                    type = ArgType.StringType
                    description = "语言过滤"
                }

                option("sort") {
                    names = listOf("--sort")
                    type = ArgType.StringType
                    choices = listOf("stars", "updated")
                    description = "排序字段"
                }

                option("order") {
                    names = listOf("--order")
                    type = ArgType.StringType
                    choices = listOf("desc", "asc")
                    description = "排序方向"
                }

                execute { args ->
                    source.reply(handleSearch(args.toSearchParams()))
                }
            }

            example("github search lukosbot --top=5 --lang=java --sort=stars --order=desc")
        }
    }

    override fun definition() = commandDefinition

    private fun handleUser(username: String): String {
        return runCatching {
            val obj = api.getUser(username)
            GitHubUser.from(obj).toReadableText()
        }.getOrElse { e ->
            log.warn("github user query failed: {}", username, e)
            "未找到用户，或请求失败：$username"
        }
    }

    private fun handleRepo(repoArg: String): String {
        val parts = repoArg.split("/", limit = 2)
        if (parts.size != 2) return "仓库格式应为：owner/repo"

        return runCatching {
            val obj = api.getRepo(parts[0], parts[1])
            GitHubRepo.from(obj).toReadableText()
        }.getOrElse { e ->
            log.warn("github repo query failed: {}", repoArg, e)
            "未找到仓库，或请求失败：$repoArg"
        }
    }

    private fun handleSearch(params: SearchParams): String {
        return runCatching {
            val json = api.searchRepos(
                params.keywords, params.sort, params.order, params.language, params.top
            )
            GitHubSearchResult.from(json, top = params.top).toReadableText()
        }.getOrElse { e ->
            log.warn("github search failed: {}", params.keywords, e)
            "搜索失败：${e.message ?: "未知错误"}"
        }
    }

    private fun ArgvParseResult.toSearchParams() = SearchParams(
        keywords = get("keyword"),
        top = getOrNull("top") ?: 3,
        language = getOrNull("lang"),
        sort = getOrNull("sort"),
        order = getOrNull("order")
    )

}
