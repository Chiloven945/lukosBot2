package top.chiloven.lukosbot2.commands.impl.github;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.config.CommandConfigProp;
import top.chiloven.lukosbot2.core.command.CommandSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The /github command for GitHub queries. Allow querying user info, repo info, and searching repos.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "github",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class GitHubCommand implements IBotCommand {
    private final GitHubApi api;

    public GitHubCommand(CommandConfigProp ccp) {
        this.api = new GitHubApi(ccp.getGitHub().getToken());
    }

    /**
     * Parsed parameters for /github search
     *
     * @param keywords search keywords
     * @param top      number of results to return (max 10)
     * @param language programming language filter
     * @param sort     sort field (stars, updated)
     * @param order    sort order (desc, asc)
     */
    private record Params(String keywords, int top, String language, String sort, String order) {
        private Params(String keywords, int top, String language, String sort, String order) {
            this.keywords = keywords.isBlank() ? "java" : keywords;
            this.top = (top <= 0) ? 3 : Math.min(top, 10);
            this.language = language;
            this.sort = sort;
            this.order = order;
        }

        static Params parse(String input) {
            ArrayList<String> toks = new ArrayList<>(Arrays.asList(input.trim().split("\\s+")));

            Map<String, String> opts = toks.stream()
                    .filter(t -> t.startsWith("--"))
                    .map(t -> {
                        int eq = t.indexOf('=');
                        return (eq > 2) ? new String[]{t.substring(2, eq), t.substring(eq + 1)} : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(a -> a[0], a -> a[1], (a, b) -> b));

            String keywords = toks.stream()
                    .filter(t -> !t.startsWith("--") || t.indexOf('=') <= 2)
                    .collect(Collectors.joining(" "));

            int top = 3;
            try {
                if (opts.containsKey("top")) top = Integer.parseInt(opts.get("top"));
            } catch (Exception ignored) {
            }

            return new Params(keywords, top, opts.get("lang"), opts.get("sort"), opts.get("order"));
        }
    }

    private static String get(JsonObject obj, String k) {
        return obj.has(k) && !obj.get(k).isJsonNull() ? obj.get(k).getAsString() : null;
    }

    private static int getInt(JsonObject obj, String k) {
        return obj.has(k) && !obj.get(k).isJsonNull() ? obj.get(k).getAsInt() : 0;
    }

    @Override
    public String name() {
        return "github";
    }

    // ===== 子命令实现 =====

    @Override
    public String description() {
        return "GitHub 查询工具";
    }

    @Override
    public String usage() {
        return """
                用法：
                `/github user <username>`     # 查询用户信息
                `/github repo <owner>/<repo>` # 查询仓库信息
                `/github search <keyword> [--top=<num>] [--lang=<lang>] [--sort=stars|updated] [--order=desc|asc]` - 搜索仓库
                示例：
                `/github user GitHub`
                `/github repo Chiloven945/lukosbot2`
                `/github search lukosbot --top=5 --lang=java --sort=stars --order=desc`
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        .then(literal("user")
                                .then(argument("username", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String username = StringArgumentType.getString(ctx, "username");
                                            ctx.getSource().reply(handleUser(username));
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("repo")
                                .then(argument("repo", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String repoArg = StringArgumentType.getString(ctx, "repo");
                                            ctx.getSource().reply(handleRepo(repoArg));
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("search")
                                .then(argument("query", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String query = StringArgumentType.getString(ctx, "query");
                                            ctx.getSource().reply(handleSearch(query));
                                            return 1;
                                        })
                                )
                        )
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
        );
    }

    private String handleUser(String username) {
        try {
            JsonObject obj = api.getUser(username);
            String login = get(obj, "login");
            String name = get(obj, "name");
            String url = get(obj, "html_url");
            int repos = getInt(obj, "public_repos");
            int followers = getInt(obj, "followers");
            int following = getInt(obj, "following");
            return String.format("""
                            用户: %s (%s)
                            主页: %s
                            公开仓库: %d | 粉丝: %d | 关注: %d
                            """,
                    (name == null || name.isBlank()) ? login : name, login, url, repos, followers, following
            );
        } catch (Exception e) {
            log.warn("github user 查询失败: {}", username, e);
            return "找不到用户或请求失败：" + username;
        }
    }

    // ===== 小工具 =====

    private String handleRepo(String repoArg) {
        try {
            String[] parts = repoArg.split("/", 2);
            if (parts.length != 2) return "仓库格式应为 owner/repo";
            JsonObject obj = api.getRepo(parts[0], parts[1]);

            String fullName = get(obj, "full_name");
            String url = get(obj, "html_url");
            String lang = get(obj, "language");
            int stars = getInt(obj, "stargazers_count");
            int forks = getInt(obj, "forks_count");
            String desc = get(obj, "description");
            return String.format("""
                            仓库: %s
                            主页: %s
                            语言: %s | Star: %d | Fork: %d
                            描述: %s
                            """,
                    fullName, url, lang, stars, forks, (desc == null || desc.isBlank()) ? "无" : desc
            );
        } catch (Exception e) {
            log.warn("github repo 查询失败: {}", repoArg, e);
            return "找不到仓库或请求失败：" + repoArg;
        }
    }

    /**
     * Deal with /github search ...
     *
     * @param q the full query string
     * @return the result text
     */
    private String handleSearch(String q) {
        try {
            Params p = Params.parse(q);
            JsonObject result = api.searchRepos(p.keywords, p.sort, p.order, p.language, p.top);

            var items = result.getAsJsonArray("items");
            if (items == null || items.isEmpty()) return "未搜索到任何仓库。";
            int count = Math.min(items.size(), p.top);
            StringBuilder sb = new StringBuilder("【仓库搜索结果】\n");
            for (int i = 0; i < count; i++) {
                JsonObject repo = items.get(i).getAsJsonObject();
                sb.append(get(repo, "full_name"))
                        .append(" - ").append(getInt(repo, "stargazers_count")).append("★\n")
                        .append(get(repo, "html_url")).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("github search 失败: {}", q, e);
            return "搜索失败：" + e.getMessage();
        }
    }
}
