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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.data.model.ReferentOption;

import java.util.List;

public final class ReferentListViewModel extends ViewModel {

    private final MutableLiveData<ReferentListUiState> state = new MutableLiveData<>();

    public ReferentListViewModel(@NonNull String referentKey,
                                 @NonNull String title,
                                 @NonNull List<ReferentOption> options,
                                 @Nullable String initialSelectedValue) {
        state.setValue(new ReferentListUiState(referentKey, title, options, initialSelectedValue));
    }

    @NonNull
    public LiveData<ReferentListUiState> getState() {
        return state;
    }

    public void select(@NonNull String value) {
        ReferentListUiState current = state.getValue();
        if (current == null) return;
        state.setValue(current.withSelected(value));
    }

    public static final class Factory implements ViewModelProvider.Factory {
        @NonNull
        private final String referentKey;
        @NonNull
        private final String title;
        @NonNull
        private final List<ReferentOption> options;
        @Nullable
        private final String initialSelectedValue;

        public Factory(@NonNull String referentKey,
                       @NonNull String title,
                       @NonNull List<ReferentOption> options,
                       @Nullable String initialSelectedValue) {
            this.referentKey = referentKey;
            this.title = title;
            this.options = options;
            this.initialSelectedValue = initialSelectedValue;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ReferentListViewModel(referentKey, title, options, initialSelectedValue);
        }
    }
}
