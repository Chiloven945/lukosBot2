package chiloven.lukosbot2.commands.music;

import chiloven.lukosbot2.commands.BotCommand;
import chiloven.lukosbot2.commands.music.provider.MusicProvider;
import chiloven.lukosbot2.commands.music.provider.SoundCloudMusicProvider;
import chiloven.lukosbot2.commands.music.provider.SpotifyMusicProvider;
import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;
import java.util.function.Predicate;

/**
 * The music command for querying music info from streaming platforms.
 *
 * @author Chiloven945
 */
public class MusicCommand implements BotCommand {
    private final MusicProvider spotify;
    private final MusicProvider soundCloud;

    public MusicCommand(AppProperties.Music music) {
        AppProperties.Music.Spotify sp = music.getSpotify();
        this.spotify = sp.isEnabled() && sp.getClientId() != null && !sp.getClientId().isBlank() && sp.getClientSecret() != null && !sp.getClientSecret().isBlank()
                ? new SpotifyMusicProvider(sp.getClientId(), sp.getClientSecret())
                : null;

        AppProperties.Music.SoundCloud sc = music.getSoundcloud();
        this.soundCloud = (sc.isEnabled() && sc.getClientId() != null && !sc.getClientId().isBlank())
                ? new SoundCloudMusicProvider(sc.getClientId())
                : null;
    }

    @Override
    public String name() {
        return "music";
    }

    @Override
    public String description() {
        return "从流媒体平台查询音乐信息";
    }

    @Override
    public String usage() {
        return """
                用法：
                /music <query>
                /music <platform> <query>
                /music link <link>
                示例：
                /music Never Gonna Give You Up
                /music spotify
                /music link
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        // /music
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
                        // /music link <link>
                        .then(LiteralArgumentBuilder.<CommandSource>literal("link")
                                .then(RequiredArgumentBuilder
                                        .<CommandSource, String>argument("link", StringArgumentType.greedyString())
                                        .executes(ctx -> runLink(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "link")
                                        ))
                                )
                        )
                        // /music <query>
                        // /music <platform> <query>
                        .then(RequiredArgumentBuilder
                                .<CommandSource, String>argument("first", StringArgumentType.word())
                                // Only one argument: treat as query
                                .executes(ctx -> runSearch(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "first"),
                                        null
                                ))
                                // The second argument eats the rest: could be `<platform> <query>` or just a multi-word query
                                .then(RequiredArgumentBuilder
                                        .<CommandSource, String>argument("rest", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CommandSource src = ctx.getSource();
                                            String first = StringArgumentType.getString(ctx, "first");
                                            String rest = StringArgumentType.getString(ctx, "rest");

                                            Predicate<String> isPlatformName = s -> {
                                                if (s == null) return false;
                                                String p = s.toLowerCase();
                                                return List.of("spotify", "soundcloud", "sc").contains(p);
                                            };

                                            return isPlatformName.test(first)
                                                    // /music <platform> <query>
                                                    ? runSearch(src, rest, first)
                                                    // /music <query-with-space>
                                                    : runSearch(src, first + " " + rest, null);
                                        })
                                )
                        )
        );
    }

    private int runSearch(CommandSource src, String query, String platformName) {
        try {
            MusicProvider provider = pickProvider(platformName);
            if (provider == null) {
                src.reply("未配置可用的音乐平台（Spotify / SoundCloud），请在 config/application.yml 中设置 lukos.music.*");
                return 0;
            }
            TrackInfo info = provider.searchTrack(query);
            if (info == null) {
                src.reply("没有找到匹配的歌曲。");
                return 0;
            }
            src.reply(info.formatted());
            return 1;
        } catch (Exception e) {
            src.reply("查询失败：" + e.getMessage());
            return 0;
        }
    }

    private int runLink(CommandSource src, String link) {
        try {
            MusicProvider provider = detectProviderByLink(link);
            if (provider == null) {
                src.reply("无法识别链接所属平台，仅支持 Spotify / SoundCloud。");
                return 0;
            }
            TrackInfo info = provider.resolveLink(link);
            if (info == null) {
                src.reply("无法从链接解析到歌曲信息。");
                return 0;
            }
            src.reply(info.formatted());
            return 1;
        } catch (Exception e) {
            src.reply("解析链接失败：" + e.getMessage());
            return 0;
        }
    }

    private MusicProvider pickProvider(String platformName) {
        if (platformName == null) {
            // Spotify default, then SoundCloud
            if (spotify != null) return spotify;
            return soundCloud;
        }
        String p = platformName.toLowerCase();
        return switch (p) {
            case "s", "spotify" -> spotify;
            case "sc", "soundcloud" -> soundCloud;
            default -> null;
        };
    }

    private MusicProvider detectProviderByLink(String link) {
        if (link == null) return null;
        String s = link.toLowerCase();
        if (s.contains("open.spotify.com")) return spotify;
        if (s.contains("soundcloud.com")) return soundCloud;
        return null;
    }
}
