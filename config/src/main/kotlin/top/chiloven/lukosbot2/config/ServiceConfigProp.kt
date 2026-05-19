package top.chiloven.lukosbot2.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Service allow/deny configuration (application.yml only controls permission).
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "lukos.services")
data class ServiceConfigProp(
    /**
     * Service allow switches. Missing means allowed.
     */
    var allow: MutableMap<String, Boolean>? = LinkedHashMap(),
) {

    fun isAllowed(name: String?): Boolean {
        if (name == null) return false
        val v = allow?.get(name)
        return v == null || v
    }

}
