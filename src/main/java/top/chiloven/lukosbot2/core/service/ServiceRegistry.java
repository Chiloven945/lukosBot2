package top.chiloven.lukosbot2.core.service;

import top.chiloven.lukosbot2.services.BotService;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ServiceRegistry {

    private final Map<String, BotService> services = new LinkedHashMap<>();

    public ServiceRegistry(List<BotService> beans) {
        if (beans != null) {
            for (BotService s : beans) {
                add(s);
            }
        }
    }

    public void add(BotService service) {
        if (service == null) return;
        services.put(service.name(), service);
    }

    public Optional<BotService> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(services.get(name));
    }

    public Collection<BotService> all() {
        return services.values();
    }
}
