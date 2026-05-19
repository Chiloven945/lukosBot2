package top.chiloven.lukosbot2.core.state;

import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.state.definition.IStateDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all {@link IStateDefinition} beans.
 */
@Service
public class StateRegistry {

    private final Map<String, IStateDefinition<?>> defs;

    public StateRegistry(List<IStateDefinition<?>> list) {
        Map<String, IStateDefinition<?>> m = new LinkedHashMap<>();
        if (list != null) {
            for (IStateDefinition<?> d : list) {
                if (d == null || d.name() == null) continue;
                m.put(d.name(), d);
            }
        }
        this.defs = Collections.unmodifiableMap(m);
    }

    public Optional<IStateDefinition<?>> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(defs.get(name));
    }

    public Collection<IStateDefinition<?>> all() {
        return defs.values();
    }

    public String listNames() {
        return defs.keySet().stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

}
