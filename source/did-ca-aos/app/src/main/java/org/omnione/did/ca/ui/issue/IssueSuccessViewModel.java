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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.repository.CredentialRepository;
import org.omnione.did.ca.data.repository.CredentialStatusRepository;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class IssueSuccessViewModel extends ViewModel {

    private static final String TAG = "IssueSuccessVm";

    private final CredentialRepository repository;
    private final CredentialStatusRepository statusRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<IssueSuccessUiState> state = new MutableLiveData<>();

    @Inject
    public IssueSuccessViewModel(@NonNull CredentialRepository repository,
                                 @NonNull CredentialStatusRepository statusRepo,
                                 @ApplicationContext android.content.Context appContext) {
        this.repository = repository;
        this.statusRepo = statusRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    public LiveData<IssueSuccessUiState> getState() { return state; }

    public void loadIssued(@NonNull String vcId) {
        state.postValue(IssueSuccessUiState.loading());
        repository.fetchDetail(vcId).whenCompleteAsync((detail, err) -> {
            if (err != null || detail == null) {
                state.postValue(IssueSuccessUiState.error(R.string.notice_issue_failed_message));
                return;
            }
            state.postValue(IssueSuccessUiState.data(detail.credential));
            if (detail.statusRef != null) {
                checkStatus(detail.credential, detail.statusRef);
            }
        }, mainExecutor);
    }

    private void checkStatus(@NonNull Credential credential, @NonNull CredentialStatusRef ref) {
        statusRepo.refreshOne(ref).whenCompleteAsync((sr, err) -> {
            if (err != null || sr == null) {
                AppLog.e(TAG, "status check failed: vcId=" + ref.vcId, err);
                return;
            }
            if (sr.status != credential.status) {
                state.postValue(IssueSuccessUiState.data(credential.withStatus(sr.status)));
            }
        }, mainExecutor);
    }
}
