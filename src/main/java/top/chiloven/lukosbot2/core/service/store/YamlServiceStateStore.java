package top.chiloven.lukosbot2.core.service.store;

import top.chiloven.lukosbot2.core.service.ServiceState;
import top.chiloven.lukosbot2.model.ServiceStateDoc;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Log4j2
public class YamlServiceStateStore implements ServiceStateStore {

    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path FILE = CONFIG_DIR.resolve("service.yml");

    private final Yaml yaml;

    public YamlServiceStateStore() {
        DumperOptions opt = new DumperOptions();
        opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opt.setPrettyFlow(true);
        opt.setIndent(2);
        opt.setIndicatorIndent(1);
        opt.setSplitLines(false);
        this.yaml = new Yaml(opt);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ServiceStateDoc load() {
        try {
            if (Files.notExists(FILE)) {
                return new ServiceStateDoc(new LinkedHashMap<>(), new LinkedHashMap<>());
            }

            try (InputStream in = Files.newInputStream(FILE)) {
                Object rootObj = yaml.load(in);
                if (!(rootObj instanceof Map<?, ?> root)) {
                    return new ServiceStateDoc(new LinkedHashMap<>(), new LinkedHashMap<>());
                }

                Object defaultsObj = root.get("defaults");
                Object chatsObj = root.get("chats");

                Map<String, ServiceState> defaults = new LinkedHashMap<>();
                if (defaultsObj instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> defaults.put(String.valueOf(k), (ServiceState) v));
                }

                Map<String, Map<String, ServiceState>> chats = new LinkedHashMap<>();
                if (chatsObj instanceof Map<?, ?> cm) {
                    cm.forEach((chatKey, serviceMapObj) -> {
                        Map<String, ServiceState> perChat = new LinkedHashMap<>();
                        if (serviceMapObj instanceof Map<?, ?> sm) {
                            sm.forEach((sn, st) -> perChat.put(String.valueOf(sn), (ServiceState) st));
                        }
                        chats.put(String.valueOf(chatKey), perChat);
                    });
                }

                return new ServiceStateDoc(defaults, chats);
            }
        } catch (Exception e) {
            log.warn("Failed to load {}", FILE, e);
            return new ServiceStateDoc(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    @Override
    public void save(ServiceStateDoc doc) {
        try {
            Files.createDirectories(CONFIG_DIR);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("defaults", doc.defaults() == null ? new LinkedHashMap<>() : doc.defaults());
            root.put("chats", doc.chats() == null ? new LinkedHashMap<>() : doc.chats());

            String text = yaml.dump(root);
            Files.writeString(FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save " + FILE, e);
        }
    }
}
