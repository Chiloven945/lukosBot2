package chiloven.lukosbot2.model;

import chiloven.lukosbot2.core.service.ServiceState;

import java.util.Map;

public record ServiceStateDoc(
        Map<String, ServiceState> defaults,
        Map<String, Map<String, ServiceState>> chats
) {
}
