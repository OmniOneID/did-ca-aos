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
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.model.Oid4vciCredentialChoice;
import org.omnione.did.ca.data.model.Oid4vciIssuerSection;
import org.omnione.did.ca.data.network.CommunicationErrors;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.model.IssuableCredential;
import org.omnione.did.ca.data.repository.IssueRepository;
import org.omnione.did.ca.data.repository.Oid4vciDiscoveryRepository;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class IssuerListViewModel extends ViewModel {

    public static final String AFTER_AUTH_TAG_PREFIX = "issue:";

    private final IssueRepository repository;
    private final Oid4vciDiscoveryRepository oid4vciDiscovery;
    private final WalletGateway walletGateway;
    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<IssuerListUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<IssuerListEvent>> event = new MutableLiveData<>();

    @NonNull private IssueProtocol protocol = IssueProtocol.OPEN_DID;

    @Nullable private IssuableCredential pendingItem;
    @Nullable private CompletableFuture<String> phase1Future;

    @Inject
    public IssuerListViewModel(IssueRepository repository,
                               Oid4vciDiscoveryRepository oid4vciDiscovery,
                               WalletGateway walletGateway,
                               BiometricRepository bioRepo,
                               @ApplicationContext android.content.Context appContext) {
        this.repository = repository;
        this.oid4vciDiscovery = oid4vciDiscovery;
        this.walletGateway = walletGateway;
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    public LiveData<IssuerListUiState> getState() { return state; }
    public LiveData<Event<IssuerListEvent>> getEvent() { return event; }
    public BiometricRepository getBioRepo() { return bioRepo; }

    public void setProtocol(@NonNull IssueProtocol protocol) { this.protocol = protocol; }
    @NonNull public IssueProtocol getProtocol() { return protocol; }

    public void loadCatalog() {
        if (protocol == IssueProtocol.OID4VCI) { loadOid4vciCatalog(); return; }
        state.postValue(IssuerListUiState.loading());
        repository.listIssuableCredentials()
                .whenCompleteAsync((items, err) -> {
                    if (err != null) {
                        state.postValue(IssuerListUiState.error(
                                R.string.notice_issue_catalog_failed_message));
                        emitError(R.string.notice_issue_catalog_failed_title,
                                R.string.notice_issue_catalog_failed_message, true, err);
                        return;
                    }
                    state.postValue(IssuerListUiState.data(items));
                }, mainExecutor);
    }

    private void loadOid4vciCatalog() {
        state.postValue(IssuerListUiState.loading());
        oid4vciDiscovery.loadCatalog().whenCompleteAsync((sections, err) -> {
            if (err != null) {
                state.postValue(IssuerListUiState.data(Collections.emptyList()));
                emitError(R.string.notice_issue_catalog_failed_title,
                        R.string.oid4vci_select_load_failed, true, err);
                return;
            }
            state.postValue(IssuerListUiState.data(flattenOid4vci(sections)));
        }, mainExecutor);
    }

    private static List<IssuableCredential> flattenOid4vci(@Nullable List<Oid4vciIssuerSection> sections) {
        List<IssuableCredential> items = new ArrayList<>();
        if (sections == null) return items;
        for (Oid4vciIssuerSection section : sections) {
            for (Oid4vciCredentialChoice c : section.credentials) {
                items.add(IssuableCredential.oid4vci(
                        section.issuerLabel, c.configurationId, badgeOf(c),
                        c.userInitiationUri, c.configurationId, c.expectedIssuer));
            }
        }
        return items;
    }

    private static CredentialBadge badgeOf(@NonNull Oid4vciCredentialChoice c) {
        if (c.sdJwtVc) return CredentialBadge.SD_JWT;
        if (c.mdoc) return CredentialBadge.MDOC;

        String fmt = c.format == null ? "" : c.format.toLowerCase();
        if (fmt.contains("sd-jwt") || (c.vct != null && !c.vct.isEmpty())) {
            return CredentialBadge.SD_JWT;
        }
        if (fmt.contains("mdoc") || fmt.contains("mso") || (c.doctype != null && !c.doctype.isEmpty())) {
            return CredentialBadge.MDOC;
        }
        return CredentialBadge.VC;
    }

    public void onCardSelected(@NonNull IssuableCredential item) {

        if (!org.omnione.did.ca.config.Constants.OID4VCI_MDOC_ENABLED && item.badge == CredentialBadge.MDOC) {
            event.postValue(new Event<>(new IssuerListEvent.ShowToast(
                    R.string.oid4vci_mdoc_not_supported)));
            return;
        }
        if (item.isOid4vci()) { onOid4vciCardSelected(item); return; }
        pendingItem = item;

        state.postValue(IssuerListUiState.issuing(currentItems()));
        phase1Future = repository.prepareIssuance(item);

        phase1Future.whenCompleteAsync((title, err) -> {
            if (err != null) {
                repository.cancelIssuance();
                pendingItem = null; phase1Future = null;
                state.postValue(IssuerListUiState.data(currentItems()));
                emitError(R.string.notice_issue_failed_title,
                        R.string.notice_issue_failed_message, false, err);
                return;
            }
            String claimUrl = repository.getClaimWebUrl();
            if (claimUrl != null) {
                event.postValue(new Event<>(new IssuerListEvent.LaunchClaimWeb(
                        claimUrl, AFTER_AUTH_TAG_PREFIX + item.vcPlanId)));
            } else {
                emitAuthLaunch(item);
            }
        }, mainExecutor);
    }

    private void onOid4vciCardSelected(@NonNull IssuableCredential item) {
        String userInitUri = item.userInitiationUri;
        String cfgId = item.oid4vciConfigurationId;
        String expectedIssuer = item.oid4vciExpectedIssuer;
        if (userInitUri == null || cfgId == null || expectedIssuer == null) {
            emitError(R.string.notice_issue_failed_title,
                    R.string.notice_issue_failed_message, false, null);
            return;
        }
        state.postValue(IssuerListUiState.issuing(currentItems()));
        oid4vciDiscovery.buildUserInitiationUrl(userInitUri, cfgId).whenCompleteAsync((url, err) -> {
            state.postValue(IssuerListUiState.data(currentItems()));
            if (err != null || url == null) {
                emitError(R.string.notice_issue_failed_title,
                        R.string.notice_issue_failed_message, false, err);
                return;
            }
            event.postValue(new Event<>(new IssuerListEvent.LaunchUserInitWeb(url, expectedIssuer)));
        }, mainExecutor);
    }

    private void emitAuthLaunch(@NonNull IssuableCredential item) {

        state.postValue(IssuerListUiState.issuing(currentItems()));
        walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
            if (err != null) {
                repository.cancelIssuance();
                pendingItem = null; phase1Future = null;
                state.postValue(IssuerListUiState.data(currentItems()));
                emitError(R.string.notice_issue_auth_failed_title,
                        R.string.notice_issue_auth_failed_message, false, err);
                return;
            }
            if (Boolean.TRUE.equals(bio)) {
                event.postValue(new Event<>(new IssuerListEvent.LaunchAuthMethodChooser(
                        AFTER_AUTH_TAG_PREFIX + item.vcPlanId)));
            } else {
                event.postValue(new Event<>(new IssuerListEvent.LaunchPin(
                        AFTER_AUTH_TAG_PREFIX + item.vcPlanId)));
            }
        }, mainExecutor);
    }

    public void onClaimWebDone() {
        if (pendingItem == null) {
            repository.cancelIssuance();
            state.postValue(IssuerListUiState.data(currentItems()));
            return;
        }
        emitAuthLaunch(pendingItem);
    }

    public void onClaimWebCancelled() {
        repository.cancelIssuance();
        pendingItem = null; phase1Future = null;
        state.postValue(IssuerListUiState.data(currentItems()));
    }

    public void onAuthDone(@Nullable String pin) {
        if (pendingItem == null || phase1Future == null) {
            repository.cancelIssuance();
            return;
        }
        List<IssuableCredential> currentItems = currentItems();
        state.postValue(IssuerListUiState.issuing(currentItems));

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
                        pendingItem = null; phase1Future = null;
                        state.postValue(IssuerListUiState.data(currentItems));
                        int titleRes = CaUtil.isAuthError(err)
                                ? R.string.notice_issue_auth_failed_title
                                : R.string.notice_issue_failed_title;
                        int msgRes = CaUtil.isAuthError(err)
                                ? R.string.notice_issue_auth_failed_message
                                : R.string.notice_issue_failed_message;
                        emitError(titleRes, msgRes, false, err);
                        return;
                    }
                    pendingItem = null; phase1Future = null;
                    state.postValue(IssuerListUiState.completed(currentItems, result.vcId, result.title));
                }, mainExecutor);
    }

    public void onBioAuthFailed() {
        repository.cancelIssuance();
        pendingItem = null; phase1Future = null;
        state.postValue(IssuerListUiState.data(currentItems()));
        emitError(R.string.notice_issue_auth_failed_title,
                R.string.notice_issue_auth_failed_message, false, null);
    }

    public void onCancelled() {
        repository.cancelIssuance();
        pendingItem = null; phase1Future = null;
        state.postValue(IssuerListUiState.data(currentItems()));
    }

    @Override
    protected void onCleared() {
        repository.cancelIssuance();
    }

    private void emitError(@StringRes int titleRes, @StringRes int messageRes,
                           boolean fatalReturnToDocs, @Nullable Throwable err) {
        event.postValue(new Event<>(new IssuerListEvent.ShowError(
                titleRes, messageRes, fatalReturnToDocs,
                CommunicationErrors.findDetailMessage(err))));
    }

    private List<IssuableCredential> currentItems() {
        IssuerListUiState s = state.getValue();
        return s == null ? Collections.emptyList() : s.items;
    }

    private static final class IssuanceResult {
        final String title;
        final String vcId;
        IssuanceResult(String title, String vcId) {
            this.title = title;
            this.vcId = vcId;
        }
    }

}
