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

package org.omnione.did.ca.ui.referent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.model.ReferentOption;

import java.util.Collections;
import java.util.List;

public final class ReferentListUiState {

    @NonNull
    public final String referentKey;
    @NonNull
    public final String title;
    @NonNull
    public final List<ReferentOption> options;
    @Nullable
    public final String selectedValue;

    public ReferentListUiState(@NonNull String referentKey,
                               @NonNull String title,
                               @NonNull List<ReferentOption> options,
                               @Nullable String selectedValue) {
        this.referentKey = referentKey;
        this.title = title;
        this.options = options;
        this.selectedValue = selectedValue;
    }

    @NonNull
    public static ReferentListUiState empty() {
        return new ReferentListUiState("", "", Collections.emptyList(), null);
    }

    @NonNull
    public ReferentListUiState withSelected(@NonNull String value) {
        return new ReferentListUiState(referentKey, title, options, value);
    }
}
