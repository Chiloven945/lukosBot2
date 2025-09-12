package chiloven.lukosbot2.lifecycle.platform;

import chiloven.lukosbot2.core.Router;
import chiloven.lukosbot2.core.SenderMux;

import java.util.List;

/**
 * Platform adapter interface.
 * Every platform adapter implements this interface and is called in SmartLifecycle.start().
 * The adapter should initialize the platform connection and return a list of resources to be closed on shutdown.
 */
public interface PlatformAdapter {
    /**
     * Platform startup, return resources that need to be closed uniformly (such as connections, threads, Client instances wrapped as AutoCloseable)
     *
     * @param router    The message router to handle incoming messages
     * @param senderMux The sender multiplexer to register platform senders
     * @return a list of AutoCloseable resources to be closed on shutdown
     * @throws Exception if startup fails
     */
    List<AutoCloseable> start(Router router, SenderMux senderMux) throws Exception;

    /**
     * Get the name of the platform adapter
     *
     * @return the name of the platform adapter
     */
    String name();
}
