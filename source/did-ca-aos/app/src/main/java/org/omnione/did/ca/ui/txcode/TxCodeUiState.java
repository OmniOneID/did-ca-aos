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

package org.omnione.did.ca.ui.txcode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class TxCodeUiState {

    public final int length;
    @NonNull public final String title;
    @NonNull public final String description;
    @NonNull public final String code;

    public TxCodeUiState(int length,
                         @NonNull String title,
                         @NonNull String description,
                         @NonNull String code) {
        this.length = length;
        this.title = title;
        this.description = description;
        this.code = code;
    }

    @NonNull
    public TxCodeUiState withCode(@NonNull String newCode) {
        return new TxCodeUiState(length, title, description, newCode);
    }

    @NonNull
    public static TxCodeUiState initial(int length,
                                        @Nullable String title,
                                        @Nullable String description,
                                        @NonNull String defaultTitle,
                                        @NonNull String defaultDescription) {
        return new TxCodeUiState(
                length,
                title == null || title.isEmpty() ? defaultTitle : title,
                description == null || description.isEmpty() ? defaultDescription : description,
                "");
    }
}
