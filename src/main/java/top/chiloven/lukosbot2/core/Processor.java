package top.chiloven.lukosbot2.core;

import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.model.MessageOut;

import java.util.Collections;
import java.util.List;

/**
 * Defines the contract for components that transform an inbound {@link MessageIn} into
 * zero or more {@link MessageOut} results; the interface also unifies “no output”
 * semantics—treating {@code null} and empty lists equivalently—via {@link #NO_OUTPUT} and
 * {@link #isEmpty(java.util.List)}, allowing pipelines to short-circuit or aggregate consistently.
 */
public interface Processor {
    /**
     * Canonical immutable empty output list used to represent “no result” from a processor; this shared constant
     * avoids per-call allocations, standardizes the semantics where {@code null} and an empty list are equivalent,
     * and is safe to share across threads since {@link java.util.Collections#emptyList()} is unmodifiable.
     */
    List<MessageOut> NO_OUTPUT = Collections.emptyList();

    /**
     * Returns whether the provided list should be considered “no output,” treating {@code null} as equivalent to
     * an empty list so callers can apply uniform control flow and aggregation logic.
     *
     * @param r the list of outputs to test (may be {@code null})
     * @return {@code true} if the list is {@code null} or empty; {@code false} otherwise
     */
    static boolean isEmpty(List<MessageOut> r) {
        return r == null || r.isEmpty();
    }

    /**
     * Processes the given inbound message and optionally produces a list of outgoing messages, returning
     * {@code null} or an empty list to indicate that this processor chose not to produce output for the input.
     *
     * @param in the inbound message to process
     * @return a list of produced messages (may be empty or {@code null} to signal no output)
     */
    List<MessageOut> handle(MessageIn in);
}
