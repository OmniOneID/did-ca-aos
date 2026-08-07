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

package org.omnione.did.ca.ui.issue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.omnione.did.ca.data.model.IssuableCredential;

import java.util.Collections;
import java.util.List;

public final class IssuerListUiState {

    public final boolean loading;
    @NonNull public final List<IssuableCredential> items;
    public final boolean inFlight;
    @Nullable public final String completedVcId;
    @Nullable public final String completedDisplayName;
    @Nullable @StringRes public final Integer errorMessageRes;

    private IssuerListUiState(boolean loading,
                              @NonNull List<IssuableCredential> items,
                              boolean inFlight,
                              @Nullable String completedVcId,
                              @Nullable String completedDisplayName,
                              @Nullable Integer errorMessageRes) {
        this.loading = loading;
        this.items = items;
        this.inFlight = inFlight;
        this.completedVcId = completedVcId;
        this.completedDisplayName = completedDisplayName;
        this.errorMessageRes = errorMessageRes;
    }

    public static IssuerListUiState loading() {
        return new IssuerListUiState(true, Collections.emptyList(), false, null, null, null);
    }

    public static IssuerListUiState data(@NonNull List<IssuableCredential> items) {
        return new IssuerListUiState(false, items, false, null, null, null);
    }

    public static IssuerListUiState issuing(@NonNull List<IssuableCredential> items) {
        return new IssuerListUiState(false, items, true, null, null, null);
    }

    public static IssuerListUiState completed(@NonNull List<IssuableCredential> items,
                                              @NonNull String vcId,
                                              @NonNull String displayName) {
        return new IssuerListUiState(false, items, false, vcId, displayName, null);
    }

    public static IssuerListUiState error(@StringRes int messageRes) {
        return new IssuerListUiState(false, Collections.emptyList(), false, null, null, messageRes);
    }
}
