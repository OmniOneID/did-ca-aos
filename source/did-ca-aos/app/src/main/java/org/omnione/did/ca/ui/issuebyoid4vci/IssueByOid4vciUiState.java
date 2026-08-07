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

package org.omnione.did.ca.ui.issuebyoid4vci;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class IssueByOid4vciUiState {

    public final boolean loading;
    public final boolean awaitingTxCode;
    @Nullable public final String completedVcId;
    @Nullable public final String completedDisplayName;

    private IssueByOid4vciUiState(boolean loading,
                                  boolean awaitingTxCode,
                                  @Nullable String completedVcId,
                                  @Nullable String completedDisplayName) {
        this.loading = loading;
        this.awaitingTxCode = awaitingTxCode;
        this.completedVcId = completedVcId;
        this.completedDisplayName = completedDisplayName;
    }

    @NonNull public static IssueByOid4vciUiState idle() {
        return new IssueByOid4vciUiState(false, false, null, null);
    }
    @NonNull public static IssueByOid4vciUiState loading() {
        return new IssueByOid4vciUiState(true, false, null, null);
    }
    @NonNull public static IssueByOid4vciUiState awaitingTxCode() {
        return new IssueByOid4vciUiState(false, true, null, null);
    }
    @NonNull public static IssueByOid4vciUiState completed(@NonNull String vcId,
                                                           @Nullable String displayName) {
        return new IssueByOid4vciUiState(false, false, vcId, displayName);
    }
}
