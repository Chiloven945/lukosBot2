package top.chiloven.lukosbot2.services;

import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.core.service.ServiceType;
import top.chiloven.lukosbot2.model.ServiceConfig;
import top.chiloven.lukosbot2.model.ServiceEvent;
import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;

import java.util.Collections;
import java.util.Map;

/**
 * Contract for all bot services.
 *
 * <p>A service is a feature module managed by {@code ServiceManager}. Services can be
 * trigger-based ({@link ServiceType#TRIGGER}) or time-based ({@link ServiceType#TIME_BASED}).</p>
 *
 * <p>All outputs must be produced via {@link CommandSource} (replaces the old {@code ServiceContext}).</p>
 */
public interface IBotService {

    /**
     * Stable service identifier (used by /service and persistence).
     */
    String name();

    /**
     * Short description shown in listings.
     */
    String description();

    /**
     * Service type.
     */
    ServiceType type();

    /**
     * Default configuration values for new chats.
     */
    default Map<String, String> defaultConfig() {
        return Collections.emptyMap();
    }

    /**
     * Callback invoked for {@link ServiceType#TIME_BASED} services when enabled.
     */
    default void onTick(CommandSource ctx, ServiceConfig config) {
        // Implement for TIME_BASED services
    }

    /**
     * Generic trigger entry point.
     *
     * <p>Default implementation forwards to {@link #onMessage(CommandSource, ServiceConfig, InboundMessage)}
     * when the event is a message event.</p>
     */
    default void onEvent(CommandSource ctx, ServiceConfig config, ServiceEvent event) {
        if (event != null && event.message() != null) {
            onMessage(ctx, config, event.message());
        }
    }

    /**
     * Callback invoked for {@link ServiceType#TRIGGER} services when enabled.
     */
    default void onMessage(CommandSource ctx, ServiceConfig config, InboundMessage in) {
        // Implement for TRIGGER services
    }

}
