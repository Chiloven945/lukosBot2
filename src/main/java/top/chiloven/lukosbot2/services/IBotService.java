package top.chiloven.lukosbot2.services;

import top.chiloven.lukosbot2.core.service.ServiceContext;
import top.chiloven.lukosbot2.core.service.ServiceType;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.model.ServiceConfig;
import top.chiloven.lukosbot2.model.ServiceEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Contract for all bot services.
 *
 * <p>A {@code BotService} represents a long-lived capability that can run
 * on a schedule (time-based) and/or react to incoming messages (trigger-based). Services are managed by the
 * {@code /service} command and are typically enabled/disabled by configuration.</p>
 *
 * <h2>Spring integration</h2>
 *
 * <p>Implementations are typically declared as Spring beans (for example using
 * {@link org.springframework.stereotype.Service}) and placed under the {@code chiloven.lukosbot2.services.*} package so
 * they can be auto-discovered.</p>
 *
 * <p>Whether a service is available can be controlled via
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}. A common pattern is to gate the
 * service bean on a configuration switch:</p>
 *
 * <pre>{@code
 * @Service
 * @ConditionalOnProperty(
 *         prefix = "lukos.services",
 *         name = "my-service",
 *         havingValue = "true",
 *         matchIfMissing = false
 * )
 * public class MyService implements BotService {
 *     ...
 * }
 * }</pre>
 *
 * <p>With the above configuration, the service is disabled by default and can be
 * enabled by setting:</p>
 *
 * <pre><code>
 * lukos.services.my-service=true
 * </code></pre>
 *
 * <h2>Execution model</h2>
 *
 * <p>The {@link #type()} declares how the service is driven:</p>
 * <ul>
 *   <li>{@link #onTick(ServiceContext, ServiceConfig)} is used for time-based services.</li>
 *   <li>{@link #onMessage(ServiceContext, ServiceConfig, MessageIn)} is used for trigger-based services.</li>
 * </ul>
 *
 * <p>The framework (service executor/dispatcher) decides when and how often to invoke
 * these callbacks based on {@link ServiceType} and the runtime configuration.</p>
 *
 * <h2>Configuration</h2>
 *
 * <p>{@link #defaultConfig()} should return stable default key-value pairs for this service,
 * which can be used to bootstrap or validate user configuration (for example when generating
 * a template or auto-filling missing entries).</p>
 *
 * @author Chiloven945
 */
public interface IBotService {

    /**
     * Returns the unique identifier of this service.
     *
     * <p>The name is used by {@code /service} management commands and may also be used
     * as a persistence key. It should be stable and unique across all services.</p>
     *
     * @return the unique service name
     */
    String name();

    /**
     * Returns a short, human-readable description of this service.
     *
     * <p>This text is typically used in {@code /service list} or status output.</p>
     *
     * @return service description
     */
    String description();

    /**
     * Returns the execution type of this service.
     *
     * <p>The runtime uses this value to decide which callbacks to invoke
     * (tick-driven, message-driven, or both depending on your enum design).</p>
     *
     * @return the service execution type
     */
    ServiceType type();

    /**
     * Returns default configuration entries for this service.
     *
     * <p>Implementations may return a map of default values used to bootstrap or
     * repair service configuration (for example filling missing keys in the config file while preserving user-provided
     * values).</p>
     *
     * @return default configuration entries; empty by default
     */
    default Map<String, String> defaultConfig() {
        return Collections.emptyMap();
    }

    /**
     * Callback invoked for TIME_BASED services when enabled.
     *
     * <p>This method is intended for periodic work such as scheduled messages,
     * polling, or maintenance tasks. The invocation frequency is defined by the service runtime and the provided
     * {@link ServiceConfig}.</p>
     *
     * @param ctx    shared runtime context (messaging, clock, etc.)
     * @param config resolved configuration for this service instance
     */
    default void onTick(ServiceContext ctx, ServiceConfig config) {
        // TBI by TIME_BASED implementations
    }

    /**
     * Generic trigger entry point.
     *
     * <p>TRIGGER services may be fired from multiple sources (chat messages, RSS poller, API watcher, etc).
     * Default behavior: if event contains a message, delegate to
     * {@link #onMessage(ServiceContext, ServiceConfig, MessageIn)}.</p>
     */
    default void onEvent(ServiceContext ctx, ServiceConfig config, ServiceEvent event) {
        if (event != null && event.message() != null) {
            onMessage(ctx, config, event.message());
        }
    }

    /**
     * Callback invoked for TRIGGER services when enabled.
     *
     * <p>This method is intended for reacting to incoming messages, such as keyword
     * triggers, rule engines, or state machines that depend on user input.</p>
     *
     * @param ctx    shared runtime context (messaging, clock, etc.)
     * @param config resolved configuration for this service instance
     * @param in     incoming message event
     */
    default void onMessage(ServiceContext ctx, ServiceConfig config, MessageIn in) {
        // TBI by TRIGGER implementations
    }
}
