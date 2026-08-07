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

public final class VpRequestedClaim {

    @NonNull
    public final String label;
    @NonNull
    public final String value;

    @NonNull
    public final String code;

    public final boolean locked;

    public VpRequestedClaim(@NonNull String label, @NonNull String value,
                            @NonNull String code, boolean locked) {
        this.label = label;
        this.value = value;
        this.code = code;
        this.locked = locked;
    }
}
