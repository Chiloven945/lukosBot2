package chiloven.lukosbot2.commands;

import chiloven.lukosbot2.core.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.apache.commons.net.whois.WhoisClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhoisCommand implements BotCommand {
    private static final Logger log = LogManager.getLogger(WhoisCommand.class);

    private final Map<String, String> whoisServerMap = new HashMap<>();

    public WhoisCommand() {
        initWhoisServers();
    }

    @Override
    public String name() {
        return "whois";
    }

    @Override
    public String description() {
        return "查询域名 Whois 信息";
    }

    @Override
    public String usage() {
        return """
                用法：
                /whois <domain> # 查询域名 Whois 信息
                示例：
                /whois baidu.com
                /whois cloudflare.com
                """;
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            ctx.getSource().reply(usage());
                            return 1;
                        })
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("domain", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ctx.getSource().reply(runQuery(StringArgumentType.getString(ctx, "domain")));
                                    return 1;
                                })
                        )
        );
    }

    public String runQuery(String domain) {
        WhoisClient wc = new WhoisClient();

        try {
            wc.connect(chooseWhoisHost(domain));
            String raw = wc.query(domain);
            return cleanWhois(raw);
        } catch (IOException e) {
            log.warn("Failed to fetch Whois information.", e);
            return e.getMessage();
        } finally {
            try {
                if (wc.isConnected()) wc.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Remove useless boilerplate (status codes help / NOTICE / TERMS, etc.)
     * from a raw WHOIS response, keeping the useful registration data.
     *
     * @param raw raw whois text
     * @return cleaned whois text
     */
    public String cleanWhois(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        Pattern boilerplateStart = Pattern.compile(
                """
                        (?im)^For more information on Whois status codes.*$\
                        |(?im)^NOTICE:.*$\
                        |(?im)^TERMS OF USE:.*$\
                        |(?im)^WHOIS Terms of Use.*$\
                        |(?im)^>>> Last update of whois database:.*$"""
        );

        Matcher m = boilerplateStart.matcher(raw);
        int cutPos = -1;
        if (m.find()) {
            cutPos = m.start();
        }

        String cleaned = (cutPos >= 0) ? raw.substring(0, cutPos) : raw;

        cleaned = cleaned
                // 1) Normalize line endings to '\n'
                .replace("\r\n", "\n").replace("\r", "\n")
                // 2) Remove lines like "<text>:" that have no value after colon
                .replaceAll("(?m)^[^\\n:]+:\\s*$\\n?", "");

        // 3) Compress duplicate "<text>: <ctx>" lines
        cleaned = compressDuplicateKeyLines(cleaned);

        cleaned = cleaned
                // 4) Collapse multiple blank lines into a single blank line
                .replaceAll("\\n{3,}", "\n\n")
                // 5) Ensure no leading/trailing newlines (trim whitespace)
                .replaceAll("^\\s+|\\s+$", "");

        return cleaned;
    }

    /**
     * Compress duplicate "<key>: <value>" lines by moving later values
     * to the first occurrence, joined by ", ".
     */
    private String compressDuplicateKeyLines(String text) {
        if (text == null || text.isEmpty()) return "";

        // Match lines like "Key: Value" where Value is not empty
        Pattern kvPattern = Pattern.compile("^\\s*([^:\\n]+)\\s*:\\s*(.+)\\s*$");

        // Preserve insertion order of first-seen keys
        LinkedHashMap<String, StringBuilder> valuesByKey = new LinkedHashMap<>();
        List<Object> order = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        // keep empty lines
        for (String line : text.split("\\n", -1)) {
            Matcher m = kvPattern.matcher(line);
            if (m.matches()) {
                String key = m.group(1).trim();
                String val = m.group(2).trim();

                // safety: treat empty val as non-kv line (should already be removed)
                if (val.isEmpty()) {
                    order.add(line);
                    continue;
                }

                if (!seen.contains(key)) {
                    seen.add(key);
                    StringBuilder sb = new StringBuilder(val);
                    valuesByKey.put(key, sb);

                    // Placeholder for first occurrence position
                    order.add(key);
                } else {
                    // Append to first occurrence
                    valuesByKey.get(key).append(", ").append(val);
                }
            } else {
                // Non "key: value" line -> keep as literal
                order.add(line);
            }
        }

        // Rebuild text in original order, but with compressed duplicates
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            Object item = order.get(i);
            if (item instanceof String key && valuesByKey.containsKey(key)) {
                out.append(key).append(": ").append(valuesByKey.get(key));
            } else {
                out.append(item.toString());
            }
            if (i < order.size() - 1) out.append('\n');
        }

        return out.toString();
    }

    public String chooseWhoisHost(String domain) {
        if (domain == null) return WhoisClient.DEFAULT_HOST;

        String d = domain.trim().toLowerCase(Locale.ROOT);
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);

        String tld = extractTld(d);
        if (tld == null) return WhoisClient.DEFAULT_HOST;

        // 1) try local map
        String mapped = whoisServerMap.get(tld);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }

        // 2) fallback: ask IANA for the TLD whois server
        String ianaServer = lookupWhoisFromIana(tld);
        return (ianaServer != null && !ianaServer.isEmpty())
                ? ianaServer
                : WhoisClient.DEFAULT_HOST;
    }

    private String lookupWhoisFromIana(String tld) {
        WhoisClient wc = new WhoisClient();
        try {
            wc.connect("whois.iana.org");
            String res = wc.query(tld);

            Matcher m = Pattern.compile("(?im)^whois:\\s*(\\S+)").matcher(res);
            if (m.find()) {
                return m.group(1).trim();
            }
            return null;
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                if (wc.isConnected()) wc.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    private String extractTld(String domain) {
        int lastDot = domain.lastIndexOf('.');
        if (lastDot < 0 || lastDot == domain.length() - 1) return null;
        return domain.substring(lastDot + 1);
    }

    private void initWhoisServers() {
        whoisServerMap.put("com", WhoisClient.DEFAULT_HOST);
        whoisServerMap.put("net", WhoisClient.DEFAULT_HOST);
        whoisServerMap.put("edu", WhoisClient.DEFAULT_HOST);

        whoisServerMap.put("cn", "whois.cnnic.cn");
        whoisServerMap.put("中国", "whois.cnnic.cn");
    }
}
