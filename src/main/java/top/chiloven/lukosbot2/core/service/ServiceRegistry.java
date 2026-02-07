package top.chiloven.lukosbot2.core.service;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.services.IBotService;

import java.util.*;

@Component
public class ServiceRegistry {

    private final Map<String, IBotService> services = new LinkedHashMap<>();

    public ServiceRegistry(List<IBotService> beans) {
        if (beans != null) {
            for (IBotService s : beans) {
                add(s);
            }
        }
    }

    public void add(IBotService service) {
        if (service == null) return;
        services.put(service.name(), service);
    }

    public Optional<IBotService> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(services.get(name));
    }

    public Collection<IBotService> all() {
        return services.values();
    }
}
