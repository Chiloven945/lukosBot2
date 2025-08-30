package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.ArrayList;
import java.util.List;

public class Pipeline implements Processor {
    private final List<Processor> chain = new ArrayList<>();

    public Pipeline add(Processor p) {
        chain.add(p);
        return this;
    }

    @Override
    public List<MessageOut> handle(MessageIn in) {
        List<MessageOut> outs = new ArrayList<>();
        for (Processor p : chain) {
            List<MessageOut> r = p.handle(in);
            if (r != null && !r.isEmpty()) outs.addAll(r);
        }
        return outs;
    }
}
