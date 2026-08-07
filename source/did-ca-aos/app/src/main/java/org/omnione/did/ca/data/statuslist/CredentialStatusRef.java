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

package org.omnione.did.ca.data.statuslist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.model.CredentialBadge;

public final class CredentialStatusRef {

    @NonNull public final String vcId;
    @NonNull public final CredentialBadge badge;
    @Nullable public final StatusListReference listRef;
    @Nullable public final Long expEpochSec;

    private CredentialStatusRef(@NonNull String vcId,
                                @NonNull CredentialBadge badge,
                                @Nullable StatusListReference listRef,
                                @Nullable Long expEpochSec) {
        this.vcId = vcId;
        this.badge = badge;
        this.listRef = listRef;
        this.expEpochSec = expEpochSec;
    }

    @NonNull
    public static CredentialStatusRef openDid(@NonNull String vcId, @Nullable Long expEpochSec) {
        return new CredentialStatusRef(vcId, CredentialBadge.VC, null, expEpochSec);
    }

    @NonNull
    public static CredentialStatusRef sdJwt(@NonNull String vcId,
                                            @Nullable StatusListReference listRef,
                                            @Nullable Long expEpochSec) {
        return new CredentialStatusRef(vcId, CredentialBadge.SD_JWT, listRef, expEpochSec);
    }
}
