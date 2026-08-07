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

package org.omnione.did.ca.ui.vc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.model.CredentialClaim;

import java.util.Collections;
import java.util.List;

public final class VcDetailUiState {

    public final boolean loading;
    public final boolean deleting;
    @Nullable public final Credential credential;
    @NonNull  public final List<CredentialClaim> certificateClaims;
    @Nullable public final List<CredentialClaim> zkpProofs;
    @Nullable @StringRes public final Integer errorMessageRes;

    private VcDetailUiState(boolean loading,
                            boolean deleting,
                            @Nullable Credential credential,
                            @NonNull List<CredentialClaim> certificateClaims,
                            @Nullable List<CredentialClaim> zkpProofs,
                            @Nullable Integer errorMessageRes) {
        this.loading = loading;
        this.deleting = deleting;
        this.credential = credential;
        this.certificateClaims = certificateClaims;
        this.zkpProofs = zkpProofs;
        this.errorMessageRes = errorMessageRes;
    }

    @NonNull public static VcDetailUiState loading() {
        return new VcDetailUiState(true, false, null, Collections.emptyList(), null, null);
    }

    @NonNull public static VcDetailUiState data(@NonNull Credential credential,
                                                @NonNull List<CredentialClaim> claims,
                                                @Nullable List<CredentialClaim> zkpProofs) {
        return new VcDetailUiState(false, false, credential, claims, zkpProofs, null);
    }

    @NonNull public static VcDetailUiState error(@StringRes int messageRes) {
        return new VcDetailUiState(false, false, null, Collections.emptyList(), null, messageRes);
    }

    @NonNull public VcDetailUiState withDeleting(boolean deleting) {
        return new VcDetailUiState(loading, deleting, credential, certificateClaims, zkpProofs, errorMessageRes);
    }
}
