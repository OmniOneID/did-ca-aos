/*
 * Copyright 2026 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.ca.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VpZkpRequest {

    @NonNull
    public final List<VpRevealAttribute> attributes;
    @NonNull
    public final List<VpPlainAttribute> predicates;
    @NonNull
    public final List<VpPlainAttribute> selfAttributes;

    @NonNull
    public final Map<String, ZkpAttributeReferent> referentsByLabel;

    public VpZkpRequest(@NonNull List<VpRevealAttribute> attributes,
                        @NonNull List<VpPlainAttribute> predicates,
                        @NonNull List<VpPlainAttribute> selfAttributes,
                        @Nullable Map<String, ZkpAttributeReferent> referentsByLabel) {
        this.attributes = attributes;
        this.predicates = predicates;
        this.selfAttributes = selfAttributes;
        this.referentsByLabel = referentsByLabel != null
                ? referentsByLabel
                : Collections.emptyMap();
    }
}
