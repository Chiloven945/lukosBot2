package top.chiloven.lukosbot2.model.message.inbound;

import top.chiloven.lukosbot2.model.message.Address;

/**
 * Chat information of an inbound message.
 */
public record Chat(
        Address addr,
        String title
) {

    public Chat {
        if (title != null && title.isBlank()) title = null;
    }

}
