package top.chiloven.lukosbot2.i18n;

import lombok.Getter;

@Getter
public enum SupportedLanguages {
    EN_US("en_us", "English (US)"),
    ZH_CN("zh_cn", "简体中文（中国大陆）"),
    ZH_TW("zh_tw", "繁體中文（台灣）");

    private final String id;
    private final String displayName;

    SupportedLanguages(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.id;
    }
}
