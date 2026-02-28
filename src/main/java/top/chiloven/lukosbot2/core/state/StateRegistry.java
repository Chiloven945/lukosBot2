package top.chiloven.lukosbot2.core.state;

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.state.definition.StateDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all {@link StateDefinition} beans.
 */
@Service
public class StateRegistry {

    private final Map<String, StateDefinition<?>> defs;

    public StateRegistry(List<StateDefinition<?>> list) {
        Map<String, StateDefinition<?>> m = new LinkedHashMap<>();
        if (list != null) {
            for (StateDefinition<?> d : list) {
                if (d == null || d.name() == null) continue;
                m.put(d.name(), d);
            }
        }
        this.defs = Collections.unmodifiableMap(m);
    }

    public Optional<StateDefinition<?>> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(defs.get(name));
    }

    public Collection<StateDefinition<?>> all() {
        return defs.values();
    }

    public String listNames() {
        return defs.keySet().stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

}
