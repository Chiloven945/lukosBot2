package chiloven.lukosbot2.lifecycle;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Log4j2
public class ConfigLifecycle implements SmartLifecycle {

    private static final String TEMPLATE_CLASSPATH = "application-template.yml";
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("application.yml");

    private volatile boolean running = false;

    /// Read as LinkedHashMap to preserve insertion order (empty file → empty Map)
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYamlMap(Yaml yaml, InputStream in) throws Exception {
        try (in) {
            Object obj = yaml.load(in);
            return (obj instanceof Map) ? (Map<String, Object>) obj : new LinkedHashMap<>();
        }
    }

    /**
     * Deep merge two maps, preserving the order of the first map.<br>
     * - If a key exists in both maps and both values are maps, merge them recursively.<br>
     * - If a key exists in both maps and at least one value is not a map, use the value from the second map.<br>
     * - Keys that exist only in the second map are appended at the end, preserving their order.<br>
     * Note: This method clones the values to avoid modifying the input maps.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMergeOrdered(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 1) Write in the order of the "defaults" map first
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            String k = e.getKey();
            Object dv = e.getValue();
            if (user.containsKey(k)) {
                Object uv = user.get(k);
                if (dv instanceof Map && uv instanceof Map) {
                    out.put(k, deepMergeOrdered((Map<String, Object>) dv, (Map<String, Object>) uv));
                } else {
                    out.put(k, uv);
                }
            } else {
                out.put(k, cloneNode(dv));
            }
        }
        // 3) For keys that are only in the "user" map, append them at the end originally
        for (Map.Entry<String, Object> ue : user.entrySet()) {
            if (!out.containsKey(ue.getKey())) {
                out.put(ue.getKey(), cloneNode(ue.getValue()));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void synchronizeOneBotAndShiro(Map<String, Object> root) {
        // 1) 取 onebot.enabled
        boolean onebotEnabled = false;
        Object lukosObj = root.get("lukos");
        if (lukosObj instanceof Map) {
            Object onebotObj = ((Map<String, Object>) lukosObj).get("onebot");
            if (onebotObj instanceof Map) {
                Object enabled = ((Map<String, Object>) onebotObj).get("enabled");
                if (enabled instanceof Boolean) {
                    onebotEnabled = (Boolean) enabled;
                }
            }
        }

        // 2) 定位 shiro.ws
        Object shiroObj = root.get("shiro");
        if (!(shiroObj instanceof Map)) return;
        Map<String, Object> shiro = (Map<String, Object>) shiroObj;
        Object wsObj = shiro.get("ws");
        if (!(wsObj instanceof Map)) return;
        Map<String, Object> ws = (Map<String, Object>) wsObj;

        // 3) client/server 开关同步
        if (onebotEnabled) {
            // 默认开 client
            Object clientObj = ws.get("client");
            if (clientObj instanceof Map) {
                ((Map<String, Object>) clientObj).put("enable", true);
            }
            // 也可以选择保持 server 原值不变，除非需要强制二选一
        } else {
            // 全部关掉
            for (String k : List.of("client", "server")) {
                Object sub = ws.get(k);
                if (sub instanceof Map) {
                    ((Map<String, Object>) sub).put("enable", false);
                }
            }
        }
    }

    /// Use "block style + indent 2 + no forced line breaks" for readability
    private static String dumpCanonical(Map<String, Object> root) {
        DumperOptions opt = new DumperOptions();
        opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opt.setPrettyFlow(true);
        opt.setIndent(2);
        opt.setIndicatorIndent(1);
        opt.setSplitLines(false);

        Yaml dumper = new Yaml(opt);
        return dumper.dump(root);
    }


    @SuppressWarnings("unchecked")
    private static Object cloneNode(Object v) {
        if (v instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<>();
            ((Map<String, Object>) v).forEach((k, val) -> m.put(k, cloneNode(val)));
            return m;
        } else if (v instanceof List) {
            List<Object> l = new ArrayList<>();
            for (Object o : (List<?>) v) l.add(cloneNode(o));
            return l;
        } else {
            return v;
        }
    }

    private static String normalizeLf(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    @Override
    public int getPhase() {
        return -1000;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void start() {
        try {
            // 1) On first run: if not exists, generate from template
            if (Files.notExists(CONFIG_FILE)) {
                Files.createDirectories(CONFIG_DIR);
                try (InputStream in = new ClassPathResource(TEMPLATE_CLASSPATH).getInputStream()) {
                    Files.copy(in, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
                }
                log.warn("Generated ./config/application.yml from {}. Please review it.", TEMPLATE_CLASSPATH);
            }

            // 2) Use template order to deep merge, and write back in "block style + indent 2"
            Yaml loader = new Yaml();
            Map<String, Object> defaults = readYamlMap(loader,
                    new ClassPathResource(TEMPLATE_CLASSPATH).getInputStream());
            Map<String, Object> user = readYamlMap(loader, Files.newInputStream(CONFIG_FILE));

            // Use the key order of the "template" as the base for deep merging (user values preferred)
            Map<String, Object> merged = deepMergeOrdered(defaults, user);

            synchronizeOneBotAndShiro(merged);

            String oldText = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            String newText = dumpCanonical(merged);

            if (!normalizeLf(oldText).equals(normalizeLf(newText))) {
                Files.writeString(CONFIG_FILE, newText, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Rewrote ./config/application.yml with canonical order/format.");
            } else {
                log.debug("Config already canonical; nothing to rewrite.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to bootstrap/repair ./config/application.yml", e);
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }
}
