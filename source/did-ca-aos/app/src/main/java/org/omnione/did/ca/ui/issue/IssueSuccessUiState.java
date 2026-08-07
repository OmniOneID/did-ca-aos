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

import org.omnione.did.ca.data.model.Credential;

public final class IssueSuccessUiState {

    public final boolean loading;
    @Nullable public final Credential credential;
    @Nullable @StringRes public final Integer errorMessageRes;

    private IssueSuccessUiState(boolean loading,
                                @Nullable Credential credential,
                                @Nullable Integer errorMessageRes) {
        this.loading = loading;
        this.credential = credential;
        this.errorMessageRes = errorMessageRes;
    }

    public static IssueSuccessUiState loading() {
        return new IssueSuccessUiState(true, null, null);
    }
    public static IssueSuccessUiState data(@NonNull Credential credential) {
        return new IssueSuccessUiState(false, credential, null);
    }
    public static IssueSuccessUiState error(@StringRes int messageRes) {
        return new IssueSuccessUiState(false, null, messageRes);
    }
}
