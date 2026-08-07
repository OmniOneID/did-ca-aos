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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.repository.Oid4vciIssueRepository;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.sdk.core.oid4vc.model.CredentialOfferResponse;
import org.omnione.did.sdk.core.oid4vc.model.IssuerMetadataResponse;
import org.omnione.did.sdk.core.oid4vc.model.TxCodeSpec;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.Locale;
import java.util.concurrent.CompletionException;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class IssueByOid4vciViewModel extends ViewModel {

    public static final String AFTER_AUTH_TAG_PREFIX = "issueOid4vci:";

    private final Oid4vciIssueRepository repo;
    private final WalletGateway walletGateway;
    private final BiometricRepository bioRepo;

    private final MutableLiveData<IssueByOid4vciUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<IssueByOid4vciEvent>> event = new MutableLiveData<>();

    @Nullable private volatile CredentialOfferResponse offer;
    @Nullable private volatile IssuerMetadataResponse metadata;
    @Nullable private volatile String configurationId;

    @Inject
    public IssueByOid4vciViewModel(Oid4vciIssueRepository repo,
                                   WalletGateway walletGateway,
                                   BiometricRepository bioRepo) {
        this.repo = repo;
        this.walletGateway = walletGateway;
        this.bioRepo = bioRepo;
    }

    @NonNull public LiveData<IssueByOid4vciUiState> getState() { return state; }
    @NonNull public LiveData<Event<IssueByOid4vciEvent>> getEvent() { return event; }
    @NonNull public BiometricRepository getBioRepo() { return bioRepo; }

    public void start(@NonNull String rawPayload) {
        start(rawPayload,  null);
    }

    public void start(@NonNull String rawPayload, @Nullable String expectedIssuer) {
        state.postValue(IssueByOid4vciUiState.loading());
        repo.parseOffer(rawPayload).whenComplete((parsedOffer, parseErr) -> {
            if (parseErr != null) { emitError(parseErr); return; }
            if (parsedOffer.getCredentialConfigurationIds().isEmpty()) {
                emitGenericError(null);
                return;
            }
            if (expectedIssuer != null
                    && !issuerMatches(parsedOffer.getCredentialIssuer(), expectedIssuer)) {
                emitGenericError("Issuer mismatch");
                return;
            }
            offer = parsedOffer;
            configurationId = parsedOffer.getCredentialConfigurationIds().get(0);
            repo.fetchMetadata(parsedOffer.getCredentialIssuer()).whenComplete((meta, metaErr) -> {
                if (metaErr != null) { emitError(metaErr); return; }
                metadata = meta;
                TxCodeSpec txCodeSpec = parsedOffer.getTxCodeSpec();
                if (txCodeSpec != null) {
                    state.postValue(IssueByOid4vciUiState.awaitingTxCode());

                    event.postValue(new Event<>(new IssueByOid4vciEvent.LaunchTxCode(
                            txCodeSpec.getLength(),
                            txCodeSpec.getInputMode().name().toLowerCase(Locale.ROOT),
                            txCodeSpec.getDescription())));
                } else {
                    proceedToToken( null);
                }
            });
        });
    }

    public void onTxCodeEntered(@NonNull String txCode) {
        state.postValue(IssueByOid4vciUiState.loading());
        proceedToToken(txCode);
    }

    public void onTxCodeCancelled() {
        repo.cancelIssuance();
        state.postValue(IssueByOid4vciUiState.idle());
    }

    private void proceedToToken(@Nullable String txCode) {
        repo.requestToken(txCode).whenComplete((unused, tokenErr) -> {
            if (tokenErr != null) { emitError(tokenErr); return; }
            decideAuth();
        });
    }

    private void decideAuth() {
        String cfgId = configurationId;
        if (cfgId == null) { emitGenericError(null); return; }
        walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenComplete((bio, e2) -> {
            if (e2 != null) { emitError(e2); return; }
            if (Boolean.TRUE.equals(bio)) {
                event.postValue(new Event<>(new IssueByOid4vciEvent.LaunchAuthMethodChooser(
                        AFTER_AUTH_TAG_PREFIX + cfgId)));
            } else {
                event.postValue(new Event<>(new IssueByOid4vciEvent.LaunchPin(
                        AFTER_AUTH_TAG_PREFIX + cfgId)));
            }
        });
    }

    public void onAuthDone(@Nullable String pin) {
        String cfgId = configurationId;
        if (cfgId == null) { emitGenericError(null); return; }
        state.postValue(IssueByOid4vciUiState.loading());
        String passcode = (pin == null) ? "" : pin;
        repo.requestAndStoreCredential(cfgId, passcode).whenComplete((vcId, credErr) -> {
            if (credErr != null) { emitError(credErr); return; }
            state.postValue(IssueByOid4vciUiState.completed(vcId, cfgId));
        });
    }

    public void onBioAuthFailed() {
        repo.cancelIssuance();
        state.postValue(IssueByOid4vciUiState.idle());
        event.postValue(new Event<>(new IssueByOid4vciEvent.ShowError(
                R.string.oid4vci_failed, R.string.oid4vci_failed, null)));
    }

    public void onAuthCancelled() {
        repo.cancelIssuance();
        state.postValue(IssueByOid4vciUiState.idle());
    }

    private void emitError(@NonNull Throwable raw) {
        repo.cancelIssuance();
        Throwable cause = raw instanceof CompletionException && raw.getCause() != null
                ? raw.getCause() : raw;
        state.postValue(IssueByOid4vciUiState.idle());
        emitGenericError(cause.getMessage());
    }

    private void emitGenericError(@Nullable String detail) {
        state.postValue(IssueByOid4vciUiState.idle());
        event.postValue(new Event<>(new IssueByOid4vciEvent.ShowError(
                R.string.oid4vci_failed, R.string.oid4vci_failed, detail)));
    }

    private static boolean issuerMatches(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return false;
        try {
            java.net.URI ua = java.net.URI.create(a.trim()).normalize();
            java.net.URI ub = java.net.URI.create(b.trim()).normalize();
            String schemeA = ua.getScheme() == null ? "" : ua.getScheme().toLowerCase();
            String schemeB = ub.getScheme() == null ? "" : ub.getScheme().toLowerCase();
            String hostA = ua.getHost() == null ? "" : ua.getHost().toLowerCase();
            String hostB = ub.getHost() == null ? "" : ub.getHost().toLowerCase();
            String pathA = stripTrailingSlash(ua.getPath());
            String pathB = stripTrailingSlash(ub.getPath());
            return schemeA.equals(schemeB) && hostA.equals(hostB)
                    && ua.getPort() == ub.getPort() && pathA.equals(pathB);
        } catch (Exception e) {
            return a.trim().equals(b.trim());
        }
    }

    private static String stripTrailingSlash(@Nullable String path) {
        if (path == null || path.isEmpty()) return "";
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    @Override
    protected void onCleared() {
        repo.cancelIssuance();
        super.onCleared();
    }
}
