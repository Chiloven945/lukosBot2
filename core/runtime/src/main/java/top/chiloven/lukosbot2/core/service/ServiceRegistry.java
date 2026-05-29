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
package top.chiloven.lukosbot2.core.service;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.services.IBotService;

import java.util.*;

@Component
public class ServiceRegistry {

    private final Map<String, IBotService> services = new LinkedHashMap<>();

    public ServiceRegistry(List<IBotService> beans) {
        if (beans != null) {
            for (IBotService s : beans) {
                add(s);
            }
        }
    }

    public void add(IBotService service) {
        if (service == null) return;
        services.put(service.name(), service);
    }

    public Optional<IBotService> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(services.get(name));
    }

    public Collection<IBotService> all() {
        return services.values();
    }

}
