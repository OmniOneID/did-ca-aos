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

import org.omnione.did.ca.data.statuslist.CredentialStatusRef;

import java.util.List;

public final class VcDetailResult {

    @NonNull public final Credential credential;
    @NonNull public final List<CredentialClaim> claims;
    @Nullable public final List<CredentialClaim> zkpClaims;
    @Nullable public final CredentialStatusRef statusRef;

    public VcDetailResult(@NonNull Credential credential,
                          @NonNull List<CredentialClaim> claims,
                          @Nullable List<CredentialClaim> zkpClaims) {
        this(credential, claims, zkpClaims, null);
    }

    public VcDetailResult(@NonNull Credential credential,
                          @NonNull List<CredentialClaim> claims,
                          @Nullable List<CredentialClaim> zkpClaims,
                          @Nullable CredentialStatusRef statusRef) {
        this.credential = credential;
        this.claims = claims;
        this.zkpClaims = zkpClaims;
        this.statusRef = statusRef;
    }
}
