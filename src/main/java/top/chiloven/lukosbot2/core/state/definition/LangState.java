package top.chiloven.lukosbot2.core.state.definition;

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.state.ScopeType;
import top.chiloven.lukosbot2.util.I18n;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
public class LangState implements StateDefinition<String> {

    @Override
    public String name() {
        return "language";
    }

    @Override
    public String namespace() {
        return "language";
    }

    @Override
    public Class<String> type() {
        return String.class;
    }

    @Override
    public EnumSet<ScopeType> allowedScopes() {
        return ScopeType.all();
    }

    @Override
    public ScopeType preferredScope() {
        return ScopeType.USER;
    }

    @Override
    public String defaultValue() {
        return "en_US";
    }

    @Override
    public String parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("language is null");
        return raw.trim();
    }

    @Override
    public void validate(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("language is blank");
        Locale loc = Locale.forLanguageTag(value);
        if (loc.getLanguage().isBlank()) {
            throw new IllegalArgumentException("invalid language tag: " + value);
        }
    }

    @Override
    public String format(String value) {
        return value;
    }

    @Override
    public String description() {
        return "机器人的回复语言";
    }

    @Override
    public List<String> suggestValues() {
        return I18n.supportLanguages();
    }

}
