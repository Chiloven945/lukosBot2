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

    @Override
    public List<MessageOut> handle(MessageIn in) {
        String t = in.text() == null ? "" : in.text().trim();
        if (!t.startsWith(prefix)) return Collections.emptyList();
        return null; // 不产生输出，仅作过滤（Pipeline 继续传给后续 Processor）
    }
}
