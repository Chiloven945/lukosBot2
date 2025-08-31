package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.ArrayList;
import java.util.List;

public class PipelineProcessor implements Processor {
    private final List<Processor> chain = new ArrayList<>();

    /**
     * Add a processor to the chain.
     *
     * @param p the processor to add
     * @return this pipeline for chaining
     */
    public PipelineProcessor add(Processor p) {
        chain.add(p);
        return this;
    }

    /**
     * Call each processor in the chain in order.
     * If any processor returns non-empty output, collect them all and return.
     * If a processor returns null or empty list, continue to the next processor.
     * @param in the input message
     * @return a list of output messages from all processors
     */
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
