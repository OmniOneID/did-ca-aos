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

package org.omnione.did.ca.ui.vc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.repository.CredentialRepository;
import org.omnione.did.ca.data.repository.CredentialStatusRepository;
import org.omnione.did.ca.data.repository.RevokeRepository;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class VcDetailViewModel extends ViewModel {

    private static final String TAG = "VcDetailVm";

    private final CredentialRepository repository;
    private final RevokeRepository revokeRepository;
    private final BiometricRepository bioRepo;
    private final CredentialStatusRepository statusRepo;
    private final MutableLiveData<Event<Integer>> statusWarning = new MutableLiveData<>();
    private final MutableLiveData<VcDetailUiState> state = new MutableLiveData<>(VcDetailUiState.loading());
    private final MutableLiveData<Event<Boolean>> deleteCompleted = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> deleteError = new MutableLiveData<>();
    private final MutableLiveData<Event<VcDetailEvent>> event = new MutableLiveData<>();
    private final Executor mainExecutor;

    @Nullable private String pendingVcId;
    @Nullable private VerifyAuthType.VERIFY_AUTH_TYPE pendingAuthType;
    @Nullable private String pendingPinForBio;

    @Inject
    public VcDetailViewModel(@NonNull CredentialRepository repository,
                             @NonNull RevokeRepository revokeRepository,
                             @NonNull BiometricRepository bioRepo,
                             @NonNull CredentialStatusRepository statusRepo,
                             @ApplicationContext Context appContext) {
        this.repository = repository;
        this.revokeRepository = revokeRepository;
        this.bioRepo = bioRepo;
        this.statusRepo = statusRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    public LiveData<VcDetailUiState> getState() {
        return state;
    }

    public LiveData<Event<Boolean>> getDeleteCompleted() {
        return deleteCompleted;
    }

    public LiveData<Event<Integer>> getDeleteError() {
        return deleteError;
    }

    public LiveData<Event<VcDetailEvent>> getEvent() {
        return event;
    }

    public LiveData<Event<Integer>> getStatusWarning() {
        return statusWarning;
    }

    public BiometricRepository getBioRepo() {
        return bioRepo;
    }

    public void loadDetail(@NonNull String vcId) {
        state.postValue(VcDetailUiState.loading());
        repository.fetchDetail(vcId).whenCompleteAsync((result, err) -> {
            if (err != null) {
                AppLog.e(TAG, "fetchDetail failed: vcId=" + vcId, err);
                state.postValue(VcDetailUiState.error(R.string.notice_vc_load_failed_message));
            } else {
                state.postValue(VcDetailUiState.data(result.credential, result.claims, result.zkpClaims));
                if (result.statusRef != null) {
                    checkStatus(result.statusRef);
                }
            }
        }, mainExecutor);
    }

    private void checkStatus(@NonNull CredentialStatusRef ref) {
        statusRepo.refreshOne(ref)
                .whenCompleteAsync((sr, err) -> {
                    if (err != null || sr == null) {
                        AppLog.e(TAG, "status check failed", err);
                        return;
                    }
                    VcDetailUiState cur = state.getValue();
                    if (cur == null || cur.credential == null) return;
                    if (cur.deleting) return;
                    if (sr.status != cur.credential.status) {
                        Credential updated = cur.credential.withStatus(sr.status);
                        state.postValue(VcDetailUiState.data(
                                updated, cur.certificateClaims, cur.zkpProofs));
                    }
                    if (sr.checkFailed && sr.failMessageRes != null) {
                        statusWarning.postValue(new Event<>(sr.failMessageRes));
                    }
                }, mainExecutor);
    }

    public void delete(@NonNull String vcId) {
        VcDetailUiState current = state.getValue();
        if (current != null && current.deleting) return;
        if (current != null) state.postValue(current.withDeleting(true));

        if (current != null && current.credential != null
                && current.credential.badge == CredentialBadge.SD_JWT) {
            repository.delete(vcId).whenCompleteAsync((unused, err) -> {
                if (err != null) {
                    AppLog.e(TAG, "delete (local sd-jwt) failed: vcId=" + vcId, err);
                    resetDeletingState();
                    deleteError.postValue(new Event<>(R.string.notice_revoke_failed_message));
                    return;
                }
                deleteCompleted.postValue(new Event<>(Boolean.TRUE));
            }, mainExecutor);
            return;
        }

        pendingVcId = vcId;

        revokeRepository.prepareRevoke(vcId).whenCompleteAsync((authType, err) -> {
            if (err != null) {
                AppLog.e(TAG, "prepareRevoke failed: vcId=" + vcId, err);
                resetDeletingState();
                pendingVcId = null;
                revokeRepository.cancelRevoke();
                deleteError.postValue(new Event<>(R.string.notice_revoke_failed_message));
                return;
            }
            pendingAuthType = authType;
            routeAuth(authType, vcId);
        }, mainExecutor);
    }

    private void routeAuth(@Nullable VerifyAuthType.VERIFY_AUTH_TYPE authType,
                           @NonNull String vcId) {
        if (authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN) {
            event.postValue(new Event<>(new VcDetailEvent.LaunchPin("revoke:" + vcId)));
        } else if (authType == VerifyAuthType.VERIFY_AUTH_TYPE.BIO) {
            bioRepo.isBiometricRegistered().whenCompleteAsync((bio, err) -> {
                if (err != null || !Boolean.TRUE.equals(bio)) {
                    failAuth();
                    return;
                }
                event.postValue(new Event<>(new VcDetailEvent.LaunchBio()));
            }, mainExecutor);
        } else if (authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_OR_BIO
                || authType == VerifyAuthType.VERIFY_AUTH_TYPE.ANY) {
            bioRepo.isBiometricRegistered().whenCompleteAsync((bio, err) -> {
                if (err != null) {
                    failAuth();
                    return;
                }
                if (Boolean.TRUE.equals(bio)) {
                    event.postValue(new Event<>(new VcDetailEvent.LaunchAuthMethodChooser()));
                } else {
                    event.postValue(new Event<>(new VcDetailEvent.LaunchPin("revoke:" + vcId)));
                }
            }, mainExecutor);
        } else if (authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO) {
            bioRepo.isBiometricRegistered().whenCompleteAsync((bio, err) -> {
                if (err != null || !Boolean.TRUE.equals(bio)) {
                    failAuth();
                    return;
                }
                event.postValue(new Event<>(new VcDetailEvent.LaunchPin("revoke:" + vcId)));
            }, mainExecutor);
        } else {
            event.postValue(new Event<>(new VcDetailEvent.LaunchPin("revoke:" + vcId)));
        }
    }

    private void failAuth() {
        pendingVcId = null;
        pendingAuthType = null;
        pendingPinForBio = null;
        revokeRepository.cancelRevoke();
        resetDeletingState();
        deleteError.postValue(new Event<>(R.string.notice_revoke_auth_failed_message));
    }

    public void onAuthDone(@Nullable String pin) {
        if (pendingVcId == null) {
            revokeRepository.cancelRevoke();
            return;
        }

        if (pendingAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO
                && pin != null
                && pendingPinForBio == null) {
            pendingPinForBio = pin;
            event.postValue(new Event<>(new VcDetailEvent.LaunchBio()));
            return;
        }

        String effectivePin = pin;
        if (pendingAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO
                && pin == null
                && pendingPinForBio != null) {
            effectivePin = pendingPinForBio;
            pendingPinForBio = null;
        }

        java.util.concurrent.CompletableFuture<Void> f = (effectivePin == null)
                ? revokeRepository.completeRevokeWithBio()
                : revokeRepository.completeRevokeWithPin(effectivePin);

        f.whenCompleteAsync((unused, err) -> {
            String vcId = pendingVcId;
            pendingVcId = null;
            pendingAuthType = null;
            pendingPinForBio = null;
            if (err != null) {
                AppLog.e(TAG, "completeRevoke failed: vcId=" + vcId, err);
                resetDeletingState();
                revokeRepository.cancelRevoke();
                int msgRes = CaUtil.isAuthError(err)
                        ? R.string.notice_revoke_auth_failed_message
                        : R.string.notice_revoke_failed_message;
                deleteError.postValue(new Event<>(msgRes));
                return;
            }
            deleteCompleted.postValue(new Event<>(Boolean.TRUE));
        }, mainExecutor);
    }

    public void onBioAuthFailed() {
        pendingVcId = null;
        pendingAuthType = null;
        pendingPinForBio = null;
        revokeRepository.cancelRevoke();
        resetDeletingState();
        deleteError.postValue(new Event<>(R.string.notice_revoke_auth_failed_message));
    }

    public void onCancelled() {
        pendingVcId = null;
        pendingAuthType = null;
        pendingPinForBio = null;
        revokeRepository.cancelRevoke();
        resetDeletingState();
    }

    @Override
    protected void onCleared() {
        revokeRepository.cancelRevoke();
    }

    private void resetDeletingState() {
        VcDetailUiState s = state.getValue();
        if (s != null) state.postValue(s.withDeleting(false));
    }

}
