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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.repository.CredentialRepository;
import org.omnione.did.ca.data.repository.CredentialStatusRepository;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class DocsViewModel extends ViewModel {

    private static final String TAG = "DocsVm";

    private final CredentialRepository repository;
    private final CredentialStatusRepository statusRepo;
    private final MutableLiveData<DocsUiState> state = new MutableLiveData<>(DocsUiState.empty());
    private final Executor callbackExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean fetching = new AtomicBoolean(false);

    @Inject
    public DocsViewModel(@NonNull CredentialRepository repository,
                         @NonNull CredentialStatusRepository statusRepo) {
        this.repository = repository;
        this.statusRepo = statusRepo;
    }

    public LiveData<DocsUiState> getState() {
        return state;
    }

    public void loadWallet() {
        DocsUiState current = state.getValue();
        boolean hasData = current != null && !current.isEmpty();
        if (!hasData) {
            state.postValue(DocsUiState.loading());
        }
        fetchWallet();
    }

    public void reloadAfterChange() {
        state.postValue(DocsUiState.loading());
        fetchWallet();
    }

    public void applyPendingStatusUpdates() {
        if (fetching.get() || !statusRepo.hasPending()) return;
        DocsUiState current = state.getValue();
        if (current == null || current.loading || current.isEmpty()) return;

        Map<String, VcStatus> updates = statusRepo.consumePending();
        List<Credential> next = new ArrayList<>(current.credentials.size());
        boolean changed = false;
        for (Credential c : current.credentials) {
            VcStatus updated = updates.get(c.id);
            if (updated != null && updated != c.status) {
                next.add(c.withStatus(updated));
                changed = true;
            } else {
                next.add(c);
            }
        }
        if (changed) {
            AppLog.d(TAG, "applying pending status updates: " + updates.size());
            state.postValue(DocsUiState.data(next));
        }
    }

    private void fetchWallet() {
        fetching.set(true);
        repository.fetchWallet().whenCompleteAsync((list, err) -> {
            fetching.set(false);
            if (err != null) {
                AppLog.e(TAG, "fetchWallet failed", err);
                state.postValue(DocsUiState.error(R.string.notice_vc_load_failed_message));
            } else {
                state.postValue(DocsUiState.data(list));
            }
        }, callbackExecutor);
    }
}
