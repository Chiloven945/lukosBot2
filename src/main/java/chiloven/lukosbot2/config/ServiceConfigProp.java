package chiloven.lukosbot2.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service allow/deny configuration (application.yml only controls permission).
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "lukos.services")
public class ServiceConfigProp {

    /**
     * Service allow switches. Missing means allowed.
     *
     * <pre>{@code
     * lukos:
     *   services:
     *     allow:
     *       auto-reply: true
     *       some-service: false
     * }
     * </pre>
     */
    private Map<String, Boolean> allow = new LinkedHashMap<>();

    public void setAllow(Map<String, Boolean> allow) {
        this.allow = (allow == null) ? new LinkedHashMap<>() : allow;
    }

    public boolean isAllowed(String name) {
        if (name == null) return false;
        Boolean v = allow.get(name);
        return v == null || v;
    }
}
