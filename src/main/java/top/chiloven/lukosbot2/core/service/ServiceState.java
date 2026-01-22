package top.chiloven.lukosbot2.core.service;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service state persisted to disk.
 */
@Getter
public final class ServiceState {
    @Setter
    private boolean enabled;
    private Map<String, String> config = new LinkedHashMap<>();

    public ServiceState() {
    }

    public ServiceState(boolean enabled, Map<String, String> config) {
        this.enabled = enabled;
        this.config = (config == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }

    public void setConfig(Map<String, String> config) {
        this.config = (config == null) ? new LinkedHashMap<>() : config;
    }
}
