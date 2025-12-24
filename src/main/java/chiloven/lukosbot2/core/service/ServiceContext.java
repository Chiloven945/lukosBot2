package chiloven.lukosbot2.core.service;

import chiloven.lukosbot2.core.MessageSenderHub;
import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.MessageOut;

public final class ServiceContext {
    private final MessageSenderHub senderHub;
    private final Address addr;

    public ServiceContext(MessageSenderHub senderHub, Address addr) {
        this.senderHub = senderHub;
        this.addr = addr;
    }

    public Address addr() {
        return addr;
    }

    public void reply(String text) {
        senderHub.send(MessageOut.text(addr, text));
    }

    public void send(Address to, String text) {
        senderHub.send(MessageOut.text(to, text));
    }
}
