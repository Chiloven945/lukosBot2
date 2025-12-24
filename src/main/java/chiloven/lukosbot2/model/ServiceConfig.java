package chiloven.lukosbot2.model;

import java.util.Map;

public record ServiceConfig(Map<String, String> values) {

    public String getOrDefault(String key, String def) {
        String v = values.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public boolean getBooleanOrDefault(String key, boolean def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }

    public int getIntOrDefault(String key, int def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Read {@code intervalMs} from config.
     *
     * @param def default interval in milliseconds
     * @return interval in milliseconds (>= 1)
     */
    public long intervalMs(long def) {
        long v = getLongOrDefault("intervalMs", def);
        return Math.max(1L, v);
    }

    public long getLongOrDefault(String key, long def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
