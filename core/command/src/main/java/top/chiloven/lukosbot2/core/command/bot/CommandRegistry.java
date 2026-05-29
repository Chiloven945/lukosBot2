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
package top.chiloven.lukosbot2.core.command.bot;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class CommandRegistry {

    private final List<IBotCommand> commands;
    private final Map<String, IBotCommand> index;

    public CommandRegistry(List<IBotCommand> commands) {
        this.commands = commands;
        this.index = buildIndex(commands);
    }

    private static Map<String, IBotCommand> buildIndex(List<IBotCommand> commands) {
        var map = new LinkedHashMap<String, IBotCommand>();
        for (var cmd : commands) {
            register(map, cmd, cmd.name());
            for (var alias : cmd.aliases()) {
                register(map, cmd, alias);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, IBotCommand> map, IBotCommand cmd, String key) {
        var lower = key.toLowerCase();
        var existing = map.get(lower);
        if (existing != null) {
            log.warn(
                    "Duplicate command key \"{}\": \"{}\" and \"{}\" both registered. "
                            + "Bean order determines which one is used.",
                    key,
                    existing.name(),
                    cmd.name()
            );
        } else {
            map.put(lower, cmd);
        }
    }

    public List<IBotCommand> all() {
        return Collections.unmodifiableList(commands);
    }

    public boolean contains(IBotCommand cmd) {
        return commands.contains(cmd);
    }

    /**
     * Get a command by its name (case-insensitive).
     *
     * @param name the command name
     *
     * @return the IBotCommand instance, or null if not found
     */
    public IBotCommand get(String name) {
        if (name == null) return null;
        return index.get(name.toLowerCase());
    }

}
