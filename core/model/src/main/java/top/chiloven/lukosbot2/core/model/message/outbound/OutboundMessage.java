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
package top.chiloven.lukosbot2.core.model.message.outbound;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import top.chiloven.lukosbot2.core.model.message.Address;
import top.chiloven.lukosbot2.core.model.message.media.BytesRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * New outbound message model.
 */
public record OutboundMessage(
        @NonNull Address addr,
        @Nullable List<OutPart> parts,
        DeliveryHints hints
) {

    public OutboundMessage(
            @NonNull Address addr,
            @Nullable List<OutPart> parts
    ) {
        this(addr, parts, null);
    }

    public OutboundMessage {
        if (parts == null) parts = List.of();
        if (hints == null) hints = DeliveryHints.defaults();
    }

    public static OutboundMessage text(Address addr, String text) {
        return new OutboundMessage(
                addr,
                List.of(new OutText(text)),
                DeliveryHints.defaults()
        );
    }

    public static OutboundMessage img(Address addr, OutImage... images) {
        return new OutboundMessage(
                addr,
                List.of(images),
                DeliveryHints.defaults()
        );
    }

    public static OutboundMessage imageBytesPng(Address addr, byte[] bytes, String name) {
        List<OutPart> ps = new ArrayList<>();
        ps.add(new OutImage(
                new BytesRef(bytes),
                null,
                name,
                "image/png"
        ));
        return new OutboundMessage(addr, ps, DeliveryHints.defaults());
    }

    public OutboundMessage add(OutPart p) {
        List<OutPart> ps = new ArrayList<>(parts == null ? Collections.emptyList() : parts);
        ps.add(p);
        return new OutboundMessage(addr, ps, hints);
    }

}
