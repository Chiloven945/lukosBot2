package chiloven.lukosbot2.commands.github;

import chiloven.lukosbot2.core.CommandSource;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * /github 命令（user / repo / search）)<br>
 * - /github user <username><br>
 * - /github repo <owner>/<repo><br>
 * - /github search <keywords> [--top=3] [--lang=Java] [--sort=stars|updated] [--order=desc|asc]
 */
public class GitHubCommand implements chiloven.lukosbot2.commands.BotCommand {
    private static final Logger log = LogManager.getLogger(GitHubCommand.class);
    private final GitHubApi api;

    public GitHubCommand(String token) {
        this.api = new GitHubApi(token);
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
        return "GitHub 查询工具（user/repo/search）";
    }

    @Override
    public String usage() {
        return """
                用法：
                /github user <用户名>
                /github repo <owner>/<repo>
                /github search <关键词> [--top=3] [--lang=Java] [--sort=stars|updated] [--order=desc|asc]""";
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .then(LiteralArgumentBuilder.<CommandSource>literal("user")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", greedyString())
                                        .executes(ctx -> {
                                            String username = StringArgumentType.getString(ctx, "username");
                                            ctx.getSource().reply(handleUser(username));
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("repo")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("repo", greedyString())
                                        .executes(ctx -> {
                                            String repoArg = StringArgumentType.getString(ctx, "repo");
                                            ctx.getSource().reply(handleRepo(repoArg));
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("search")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("query", greedyString())
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
            ); // 参考旧版 User#toString。&#8203;:contentReference[oaicite:5]{index=5}
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
            String[] toks = input.trim().split("\\s+");
            StringBuilder kw = new StringBuilder();
            int top = 3;
            String lang = null, sort = null, order = null;
            for (String t : toks) {
                if (t.startsWith("--top=")) {
                    try {
                        top = Integer.parseInt(t.substring(6));
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (t.startsWith("--lang=")) {
                    lang = t.substring(7);
                    continue;
                }
                if (t.startsWith("--sort=")) {
                    sort = t.substring(7);
                    continue;
                }
                if (t.startsWith("--order=")) {
                    order = t.substring(8);
                    continue;
                }
                if (!kw.isEmpty()) kw.append(' ');
                kw.append(t);
            }
            return new Params(kw.toString().trim(), top, lang, sort, order);
        }
    }
}
