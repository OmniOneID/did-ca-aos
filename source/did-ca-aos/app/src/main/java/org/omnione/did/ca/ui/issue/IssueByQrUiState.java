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

public final class IssueByQrUiState {

    public final boolean loading;

    public final boolean inFlight;

    @Nullable public final String completedVcId;

    @Nullable public final String completedDisplayName;

    private IssueByQrUiState(boolean loading, boolean inFlight,
                             @Nullable String completedVcId,
                             @Nullable String completedDisplayName) {
        this.loading = loading;
        this.inFlight = inFlight;
        this.completedVcId = completedVcId;
        this.completedDisplayName = completedDisplayName;
    }

    public static IssueByQrUiState loading() {
        return new IssueByQrUiState(true, false, null, null);
    }

    public static IssueByQrUiState idle() {
        return new IssueByQrUiState(false, false, null, null);
    }

    public static IssueByQrUiState issuing() {
        return new IssueByQrUiState(false, true, null, null);
    }

    public static IssueByQrUiState completed(@NonNull String vcId,
                                             @Nullable String displayName) {
        return new IssueByQrUiState(false, false, vcId, displayName);
    }
}
