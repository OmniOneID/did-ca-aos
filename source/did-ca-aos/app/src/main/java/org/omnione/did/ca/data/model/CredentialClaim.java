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

public final class CredentialClaim {

    public enum Kind {
        FLAT, GROUP
    }

    @NonNull
    public final Kind kind;
    @NonNull
    public final String label;
    @Nullable
    public final String value;
    @NonNull
    public final List<CredentialClaim> items;

    private CredentialClaim(@NonNull Kind kind,
                            @NonNull String label,
                            @Nullable String value,
                            @NonNull List<CredentialClaim> items) {
        this.kind = kind;
        this.label = label;
        this.value = value;
        this.items = items;
    }

    @NonNull
    public static CredentialClaim flat(@NonNull String label, @NonNull String value) {
        return new CredentialClaim(Kind.FLAT, label, value, Collections.emptyList());
    }

    @NonNull
    public static CredentialClaim group(@NonNull String label, @NonNull List<CredentialClaim> items) {
        return new CredentialClaim(Kind.GROUP, label, null, items);
    }
}
