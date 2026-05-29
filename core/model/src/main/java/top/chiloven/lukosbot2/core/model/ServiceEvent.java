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
package top.chiloven.lukosbot2.core.model;

import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;

public final class ServiceEvent {

    private final Kind kind;
    private final InboundMessage message;
    private final String key;
    private final Object payload;

    private ServiceEvent(Kind kind, InboundMessage message, String key, Object payload) {
        this.kind = kind;
        this.message = message;
        this.key = key;
        this.payload = payload;
    }

    public static ServiceEvent message(InboundMessage in) {
        return new ServiceEvent(Kind.MESSAGE, in, null, null);
    }

    public static ServiceEvent external(String key, Object payload) {
        return new ServiceEvent(Kind.EXTERNAL, null, key, payload);
    }

    public Kind kind() {
        return kind;
    }

    public InboundMessage message() {
        return message;
    }

    public String key() {
        return key;
    }

    public Object payload() {
        return payload;
    }

    public enum Kind {
        MESSAGE,
        EXTERNAL
    }

}
