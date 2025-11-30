package chiloven.lukosbot2.commands.music;

import lombok.Getter;

/**
 * Music platform supported
 */
@Getter
public enum MusicPlatform {
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud");

    private final String name;

    MusicPlatform(String name) {
        this.name = name;
    }
}
