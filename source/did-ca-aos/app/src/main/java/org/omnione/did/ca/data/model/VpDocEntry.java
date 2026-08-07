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

public final class VpDocEntry {

    @Nullable
    public final VpRequestedClaim claim;
    @Nullable
    public final VpRequestedClaimGroup group;

    private VpDocEntry(@Nullable VpRequestedClaim claim,
                       @Nullable VpRequestedClaimGroup group) {
        this.claim = claim;
        this.group = group;
    }

    @NonNull
    public static VpDocEntry of(@NonNull VpRequestedClaim claim) {
        return new VpDocEntry(claim, null);
    }

    @NonNull
    public static VpDocEntry of(@NonNull VpRequestedClaimGroup group) {
        return new VpDocEntry(null, group);
    }

    public boolean isGroup() {
        return group != null;
    }
}
