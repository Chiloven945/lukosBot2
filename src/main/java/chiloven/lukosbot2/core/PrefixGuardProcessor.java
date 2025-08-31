package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.Collections;
import java.util.List;

public class PrefixGuardProcessor implements Processor {
    private final String prefix;

    public PrefixGuardProcessor(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Only pass through messages starting with the specified prefix.
     * If the message does not start with the prefix, return an empty list to block further processing.
     * If it does start with the prefix, return null to allow further processing by subsequent processors.
     *
     * @param in the input message
     * @return a list of output messages or null
     */
    @Override
    public List<MessageOut> handle(MessageIn in) {
        String t = in.text() == null ? "" : in.text().trim();
        if (!t.startsWith(prefix)) return Collections.emptyList();
        return null;
    }
}
