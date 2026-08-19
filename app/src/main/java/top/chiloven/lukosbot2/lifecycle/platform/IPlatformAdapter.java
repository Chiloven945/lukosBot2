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

import org.springframework.context.SmartLifecycle;

/**
 * Platform adapter interface. Every platform adapter implements this interface and is driven by
 * Spring's {@link SmartLifecycle} machinery: start/stop the platform connection and launch/collect
 * the inbound message flow inside its own lifecycle.
 */
public interface IPlatformAdapter extends SmartLifecycle {

    /**
     * Get the name of the platform adapter
     *
     * @return the name of the platform adapter
     */
    String name();

}
