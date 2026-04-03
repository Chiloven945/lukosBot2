package top.chiloven.lukosbot2.util.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;

import java.util.*;

@Log4j2
public class BrigadierUtils {

    private BrigadierUtils() {
    }

    public static boolean isValidLiteral(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static <S> void registerAliases(
            @NonNull CommandDispatcher<S> dispatcher,
            @NonNull String mainCommand,
            @NonNull List<String> aliases
    ) {
        if (aliases.isEmpty()) return;

        CommandNode<S> targetNode = dispatcher.getRoot().getChild(mainCommand);
        if (targetNode == null) {
            throw new IllegalStateException("Main command not registered yet: " + mainCommand);
        }

        Set<String> seen = new LinkedHashSet<>();
        aliases.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(alias -> !alias.isEmpty())
                .filter(alias -> !alias.equalsIgnoreCase(mainCommand))
                .forEach(alias -> {
                    String dedupKey = alias.toLowerCase(Locale.ROOT);

                    if (!seen.add(dedupKey)) return;
                    if (!isValidLiteral(alias)) return;
                    if (dispatcher.getRoot().getChild(alias) != null) {
                        throw new IllegalArgumentException("Alias already exists: " + alias);
                    }

                    LiteralArgumentBuilder<S> builder = LiteralArgumentBuilder.literal(alias);

                    if (targetNode.getCommand() != null) {
                        builder.executes(targetNode.getCommand());
                    }

                    builder.requires(targetNode::canUse);
                    builder.redirect(targetNode);
                    dispatcher.register(builder);

                    log.debug("Alias {} registered for command {}.", alias, mainCommand);
                });
    }

}
