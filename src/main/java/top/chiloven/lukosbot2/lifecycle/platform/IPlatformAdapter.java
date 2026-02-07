package top.chiloven.lukosbot2.lifecycle.platform;

import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.core.MessageSenderHub;

import java.util.List;

/**
 * Platform adapter interface.
 * Every platform adapter implements this interface and is called in SmartLifecycle.start().
 * The adapter should initialize the platform connection and return a list of resources to be closed on shutdown.
 */
public interface IPlatformAdapter {
    /**
     * Platform startup, return resources that need to be closed uniformly (such as connections, threads, Client instances wrapped as AutoCloseable)
     *
     * @param md    The message md to handle incoming messages
     * @param msh The sender multiplexer to register platform senders
     * @return a list of AutoCloseable resources to be closed on shutdown
     * @throws Exception if startup fails
     */
    List<AutoCloseable> start(MessageDispatcher md, MessageSenderHub msh) throws Exception;

    /**
     * Get the name of the platform adapter
     *
     * @return the name of the platform adapter
     */
    String name();
}
