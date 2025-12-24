package chiloven.lukosbot2.core.service.store;

import chiloven.lukosbot2.model.ServiceStateDoc;

/**
 * Persistent store for service states.
 */
public interface ServiceStateStore {
    ServiceStateDoc load();

    void save(ServiceStateDoc doc);
}
