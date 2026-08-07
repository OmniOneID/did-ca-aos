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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.ui.Event;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class TxCodeViewModel extends ViewModel {

    private final MutableLiveData<TxCodeUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> txCodeReady = new MutableLiveData<>();

    @Inject
    public TxCodeViewModel() {}

    public void init(int length,
                     @Nullable String title,
                     @Nullable String description,
                     @NonNull String defaultTitle,
                     @NonNull String defaultDescription) {
        if (uiState.getValue() != null) return;
        uiState.setValue(TxCodeUiState.initial(
                Math.max(1, length), title, description, defaultTitle, defaultDescription));
    }

    @NonNull public LiveData<TxCodeUiState> uiState() { return uiState; }
    @NonNull public LiveData<Event<String>> onTxCodeReady() { return txCodeReady; }

    public void onKey(char key) {
        TxCodeUiState current = uiState.getValue();
        if (current == null) return;
        String next;
        if (key == 'd') {
            if (current.code.isEmpty()) return;
            next = current.code.substring(0, current.code.length() - 1);
        } else {
            if (current.code.length() >= current.length) return;
            next = current.code + key;
        }
        TxCodeUiState updated = current.withCode(next);
        uiState.setValue(updated);
        if (next.length() == updated.length) {
            txCodeReady.setValue(new Event<>(next));
        }
    }
}
