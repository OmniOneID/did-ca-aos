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

package org.omnione.did.ca.ui.docs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.omnione.did.ca.data.model.Credential;

import java.util.Collections;
import java.util.List;

public final class DocsUiState {

    public final boolean loading;
    @NonNull public final List<Credential> credentials;
    @Nullable @StringRes public final Integer errorMessageRes;

    private DocsUiState(boolean loading,
                        @NonNull List<Credential> credentials,
                        @Nullable Integer errorMessageRes) {
        this.loading = loading;
        this.credentials = credentials;
        this.errorMessageRes = errorMessageRes;
    }

    @NonNull public static DocsUiState empty() {
        return new DocsUiState(false, Collections.emptyList(), null);
    }

    @NonNull public static DocsUiState loading() {
        return new DocsUiState(true, Collections.emptyList(), null);
    }

    @NonNull public static DocsUiState data(@NonNull List<Credential> credentials) {
        return new DocsUiState(false, credentials, null);
    }

    @NonNull public static DocsUiState error(@StringRes int messageRes) {
        return new DocsUiState(false, Collections.emptyList(), messageRes);
    }

    public boolean isEmpty() {
        return credentials.isEmpty();
    }
}
