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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.network.CommunicationErrors;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.model.IssueOffer;
import org.omnione.did.ca.data.repository.IssueRepository;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class IssueByQrViewModel extends ViewModel {

    public static final String AFTER_AUTH_TAG_PREFIX = "issueQr:";

    private final IssueRepository repository;
    private final WalletGateway walletGateway;
    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<IssueByQrUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<IssueByQrEvent>> event = new MutableLiveData<>();

    @Nullable private IssueOffer pendingOffer;
    @Nullable private CompletableFuture<String> phase1Future;

    @Inject
    public IssueByQrViewModel(IssueRepository repository,
                              WalletGateway walletGateway,
                              BiometricRepository bioRepo,
                              @ApplicationContext Context appContext) {
        this.repository = repository;
        this.walletGateway = walletGateway;
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    public LiveData<IssueByQrUiState> getState() { return state; }
    public LiveData<Event<IssueByQrEvent>> getEvent() { return event; }
    public BiometricRepository getBioRepo() { return bioRepo; }

    public void start(@NonNull String rawPayload) {
        state.postValue(IssueByQrUiState.loading());
        repository.parseOffer(rawPayload).whenCompleteAsync((offer, err) -> {
            if (err != null) {
                state.postValue(IssueByQrUiState.idle());
                emitError(R.string.notice_qr_issue_invalid_title,
                        R.string.notice_qr_issue_invalid_message, err);
                return;
            }
            pendingOffer = offer;
            phase1Future = repository.prepareIssuanceFromOffer(
                    offer.offerId, offer.vcPlanId, offer.issuerDid);

            walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, e2) -> {
                if (e2 != null) {
                    repository.cancelIssuance();
                    pendingOffer = null; phase1Future = null;
                    state.postValue(IssueByQrUiState.idle());
                    emitError(R.string.notice_issue_auth_failed_title,
                            R.string.notice_issue_auth_failed_message, e2);
                    return;
                }
                if (Boolean.TRUE.equals(bio)) {
                    event.postValue(new Event<>(new IssueByQrEvent.LaunchAuthMethodChooser(
                            AFTER_AUTH_TAG_PREFIX + offer.vcPlanId)));
                } else {
                    event.postValue(new Event<>(new IssueByQrEvent.LaunchPin(
                            AFTER_AUTH_TAG_PREFIX + offer.vcPlanId)));
                }
            }, mainExecutor);
        }, mainExecutor);
    }

    public void onAuthDone(@Nullable String pin) {
        if (pendingOffer == null || phase1Future == null) {
            repository.cancelIssuance();
            return;
        }
        state.postValue(IssueByQrUiState.issuing());

        phase1Future
                .thenCompose(title -> ((pin == null)
                        ? repository.signWithBio()
                        : repository.signWithPin(pin))
                        .thenApply(v -> title))
                .thenCompose(title -> repository.completeIssuance()
                        .thenApply(vcId -> new IssuanceResult(title, vcId)))
                .whenCompleteAsync((result, err) -> {
                    if (err != null) {
                        repository.cancelIssuance();
                        pendingOffer = null; phase1Future = null;
                        state.postValue(IssueByQrUiState.idle());
                        int titleRes = CaUtil.isAuthError(err)
                                ? R.string.notice_issue_auth_failed_title
                                : R.string.notice_qr_issue_failed_title;
                        int msgRes = CaUtil.isAuthError(err)
                                ? R.string.notice_issue_auth_failed_message
                                : R.string.notice_qr_issue_failed_message;
                        emitError(titleRes, msgRes, err);
                        return;
                    }
                    pendingOffer = null; phase1Future = null;
                    state.postValue(IssueByQrUiState.completed(result.vcId, result.title));
                }, mainExecutor);
    }

    public void onBioAuthFailed() {
        repository.cancelIssuance();
        pendingOffer = null; phase1Future = null;
        state.postValue(IssueByQrUiState.idle());
        emitError(R.string.notice_issue_auth_failed_title,
                R.string.notice_issue_auth_failed_message, null);
    }

    private void emitError(@StringRes int titleRes, @StringRes int messageRes,
                           @Nullable Throwable err) {
        event.postValue(new Event<>(new IssueByQrEvent.ShowError(
                titleRes, messageRes, CommunicationErrors.findDetailMessage(err))));
    }

    public void onCancelled() {
        repository.cancelIssuance();
        pendingOffer = null; phase1Future = null;
    }

    @Override
    protected void onCleared() {
        repository.cancelIssuance();
    }

    private static final class IssuanceResult {
        @Nullable final String title;
        @NonNull final String vcId;
        IssuanceResult(@Nullable String title, @NonNull String vcId) {
            this.title = title;
            this.vcId = vcId;
        }
    }

}
