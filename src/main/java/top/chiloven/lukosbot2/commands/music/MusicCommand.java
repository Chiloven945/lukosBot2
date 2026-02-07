package top.chiloven.lukosbot2.commands.music;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.music.provider.IMusicProvider;
import top.chiloven.lukosbot2.commands.music.provider.SoundCloudMusicProvider;
import top.chiloven.lukosbot2.commands.music.provider.SpotifyMusicProvider;
import top.chiloven.lukosbot2.config.CommandConfigProp;
import top.chiloven.lukosbot2.core.command.CommandSource;

import java.util.List;
import java.util.function.Predicate;

import static top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * The music command for querying music info from streaming platform.
 *
 * @author Chiloven945
 */
@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "music",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class MusicCommand implements IBotCommand {
    private final IMusicProvider spotify;
    private final IMusicProvider soundCloud;

    public MusicCommand(CommandConfigProp ccp) {
        CommandConfigProp.Music.Spotify sp = ccp.getMusic().getSpotify();
        this.spotify = sp.isEnabled() && sp.getClientId() != null && !sp.getClientId().isBlank() && sp.getClientSecret() != null && !sp.getClientSecret().isBlank()
                ? new SpotifyMusicProvider(sp.getClientId(), sp.getClientSecret())
                : null;

        CommandConfigProp.Music.SoundCloud sc = ccp.getMusic().getSoundcloud();
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
                literal(name())
                        // /music
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
                        // /music link <link>
                        .then(literal("link")
                                .then(argument("link", StringArgumentType.greedyString())
                                        .executes(ctx -> runLink(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "link")
                                        ))
                                )
                        )
                        // /music <query>
                        // /music <platform> <query>
                        .then(argument("first", StringArgumentType.word())
                                // Only one argument: treat as query
                                .executes(ctx -> runSearch(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "first"),
                                        null
                                ))
                                // The second argument eats the rest: could be `<platform> <query>` or just a multi-word query
                                .then(argument("rest", StringArgumentType.greedyString())
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
            IMusicProvider provider = pickProvider(platformName);
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
            log.warn("Music search failed: query='{}', platform='{}'", query, platformName, e);
            return 0;
        }
    }

    private int runLink(CommandSource src, String link) {
        try {
            IMusicProvider provider = detectProviderByLink(link);
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
            log.warn("Music link failed: query='{}', platform='{}'", link, link, e);
            return 0;
        }
    }

    private IMusicProvider pickProvider(String platformName) {
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

    private IMusicProvider detectProviderByLink(String link) {
        if (link == null) return null;
        String s = link.toLowerCase();
        if (s.contains("open.spotify.com")) return spotify;
        if (s.contains("soundcloud.com")) return soundCloud;
        return null;
    }
}
