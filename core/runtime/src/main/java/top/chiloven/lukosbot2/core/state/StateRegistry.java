/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
