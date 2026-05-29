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
package top.chiloven.lukosbot2.lifecycle.platform;

import java.util.ArrayList;
import java.util.List;

public final class BaseCloseable implements AutoCloseable {

    private final List<AutoCloseable> list = new ArrayList<>();

    /**
     * Add a closeable resource to be closed when this is closed.
     *
     * @param c the closeable resource
     *
     * @return this instance for chaining
     */
    public synchronized BaseCloseable add(AutoCloseable c) {
        if (c != null && c != this) list.add(c);
        return this;
    }

    /**
     * Add multiple closeable resources to be closed when this is closed.
     *
     * @param cs the list of closeable resources
     *
     * @return this instance for chaining
     */
    public synchronized BaseCloseable addAll(List<? extends AutoCloseable> cs) {
        if (cs != null) {
            cs.stream()
                    .filter(c -> c != null && c != this)
                    .forEach(list::add);
        }
        return this;
    }

    @Override
    public synchronized void close() {
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                list.get(i).close();
            } catch (Exception _) {
            }
        }
        list.clear();
    }

}
