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

package org.omnione.did.ca.ui.vp;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.network.CommunicationErrors;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.repository.Oid4vpSubmissionRepository;
import org.omnione.did.ca.data.model.VerifyOfferEnvelope;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.repository.VpOfferParser;
import org.omnione.did.ca.data.repository.VpSubmissionRepository;
import org.omnione.did.ca.data.repository.ZkpSubmissionRepository;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class VpRequestViewModel extends ViewModel {

    private static final String TAG = "VpRequestVm";

    private final VpSubmissionRepository vpRepo;
    private final ZkpSubmissionRepository zkpRepo;
    private final Oid4vpSubmissionRepository oid4vpRepo;
    private final VpOfferParser parser;
    private final WalletGateway walletGateway;
    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<VpRequestUiState> state =
            new MutableLiveData<>(VpRequestUiState.initialLoading());
    private final MutableLiveData<Event<VpRequestEvent>> event = new MutableLiveData<>();

    @Nullable private VerifyOfferEnvelope pendingOffer;

    private enum Flow { OPENDID_VC, ZKP, OID4VP }
    private Flow currentFlow = Flow.OPENDID_VC;

    private boolean warnedAtLeastOnce;

    @Nullable private String pendingPinForBio;

    @Inject
    public VpRequestViewModel(VpSubmissionRepository vpRepo,
                              ZkpSubmissionRepository zkpRepo,
                              Oid4vpSubmissionRepository oid4vpRepo,
                              VpOfferParser parser,
                              WalletGateway walletGateway,
                              BiometricRepository bioRepo,
                              @ApplicationContext Context appContext) {
        this.vpRepo = vpRepo;
        this.zkpRepo = zkpRepo;
        this.oid4vpRepo = oid4vpRepo;
        this.parser = parser;
        this.walletGateway = walletGateway;
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
    }

    public LiveData<VpRequestUiState> getState() { return state; }
    public LiveData<Event<VpRequestEvent>> getEvent() { return event; }
    public BiometricRepository getBioRepo() { return bioRepo; }

    public void start(@NonNull String rawPayload) {
        AppLog.d(TAG, "start rawPayload=" + rawPayload);
        state.postValue(VpRequestUiState.initialLoading());
        parser.parseOffer(rawPayload).whenCompleteAsync((env, err) -> {
            if (err != null || env == null) {
                AppLog.e(TAG, "parseOffer failed", err);
                emitInvalid(err);
                return;
            }
            if (env.isOid4vp) {

                AppLog.d(TAG, "start OID4VP url=" + env.oid4vpUrl);
                currentFlow = Flow.OID4VP;
                oid4vpRepo.prepareOid4vpPresentation(env.oid4vpUrl).whenCompleteAsync((scenario, err2) -> {
                    if (err2 != null) {
                        AppLog.e(TAG, "prepareOid4vpPresentation failed", err2);
                        cancelActiveSession();
                        emitInvalid(err2);
                        return;
                    }
                    replaceScenario(scenario);
                }, mainExecutor);
                return;
            }
            AppLog.d(TAG, "start " + (env.isZkp ? "ZKP" : "OPENDID_VC") + " offerId=" + env.offerId + " txId=" + env.txId);
            pendingOffer = env;
            currentFlow = env.isZkp ? Flow.ZKP : Flow.OPENDID_VC;
            CompletableFuture<VpScenario> prep = env.isZkp
                    ? zkpRepo.prepareZkpPresentation(env.offerId, env.txId)
                    : vpRepo.prepareVpPresentation(env.offerId, env.txId);
            prep.whenCompleteAsync((scenario, err2) -> {
                if (err2 != null) {
                    AppLog.e(TAG, "prepare" + (env.isZkp ? "Zkp" : "Vp") + "Presentation failed", err2);
                    cancelActiveSession();
                    emitInvalid(err2);
                    return;
                }
                replaceScenario(scenario);
            }, mainExecutor);
        }, mainExecutor);
    }

    public void onSubmitClicked() {
        VpRequestUiState cur = requireState();
        if (cur.scenario == null) return;
        if (!cur.canSubmit()) return;

        if (currentFlow == Flow.ZKP) {
            onAuthDone(null);
            return;
        }

        switch (cur.scenario.authType) {
            case PIN:
                event.postValue(new Event<>(new VpRequestEvent.LaunchPin()));
                break;
            case BIO:
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null || !Boolean.TRUE.equals(bio)) {
                        emitAuthFailed();
                        return;
                    }
                    event.postValue(new Event<>(new VpRequestEvent.LaunchBio()));
                }, mainExecutor);
                break;
            case ANY:
            case PIN_OR_BIO:
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null) {
                        emitAuthFailed();
                        return;
                    }
                    if (Boolean.TRUE.equals(bio)) {
                        event.postValue(new Event<>(new VpRequestEvent.LaunchAuthMethodChooser()));
                    } else {
                        event.postValue(new Event<>(new VpRequestEvent.LaunchPin()));
                    }
                }, mainExecutor);
                break;
            case PIN_AND_BIO:
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null || !Boolean.TRUE.equals(bio)) {
                        emitError(R.string.notice_vp_bio_not_registered_title,
                                R.string.notice_vp_bio_not_registered_message);
                        return;
                    }
                    event.postValue(new Event<>(new VpRequestEvent.LaunchPin()));
                }, mainExecutor);
                break;
            case FREE:
            default:
                emitError(R.string.notice_vp_failed_title,
                        R.string.notice_vp_unsupported_authtype);
                break;
        }
    }

    public void onAuthDone(@Nullable String pin) {

        if (currentFlow != Flow.OID4VP && pendingOffer == null) {
            cancelActiveSession();
            return;
        }
        VpRequestUiState cur = requireState();

        if (cur.scenario != null
                && cur.scenario.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO
                && pin != null
                && pendingPinForBio == null) {
            pendingPinForBio = pin;
            event.postValue(new Event<>(new VpRequestEvent.LaunchBio()));
            return;
        }

        String effectivePin = pin;
        if (cur.scenario != null
                && cur.scenario.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO
                && pin == null
                && pendingPinForBio != null) {
            effectivePin = pendingPinForBio;
            pendingPinForBio = null;
        }
        final String pinForSign = effectivePin;

        state.postValue(copyWith(true, true));

        CompletableFuture<Void> signFuture;
        CompletableFuture<Void> submitFuture;
        switch (currentFlow) {
            case ZKP:

                signFuture = (pinForSign == null)
                        ? zkpRepo.signZkpWithBio(cur.selectedReferents,
                                cur.selectedReferentCredIds, cur.revealedAttrLabels)
                        : zkpRepo.signZkpWithPin(pinForSign, cur.selectedReferents,
                                cur.selectedReferentCredIds, cur.revealedAttrLabels);
                submitFuture = signFuture.thenCompose(v -> zkpRepo.submitZkpPresentation());
                break;
            case OID4VP:

                signFuture = (pinForSign == null)
                        ? oid4vpRepo.signOid4vpWithBio(cur.uncheckedClaimKeys)
                        : oid4vpRepo.signOid4vpWithPin(pinForSign, cur.uncheckedClaimKeys);
                submitFuture = signFuture.thenCompose(v -> oid4vpRepo.submitOid4vpPresentation());
                break;
            case OPENDID_VC:
            default:

                signFuture = (pinForSign == null)
                        ? vpRepo.signVpWithBio(cur.uncheckedClaimKeys)
                        : vpRepo.signVpWithPin(pinForSign, cur.uncheckedClaimKeys);
                submitFuture = signFuture.thenCompose(v -> vpRepo.submitVpPresentation());
                break;
        }

        submitFuture.whenCompleteAsync((v, err) -> {
            if (err != null) {
                cancelActiveSession();
                pendingOffer = null;
                int title = CaUtil.isAuthError(err)
                        ? R.string.notice_vp_auth_failed_title
                        : R.string.notice_vp_failed_title;
                int msg = CaUtil.isAuthError(err)
                        ? R.string.notice_vp_auth_failed_message
                        : R.string.notice_vp_failed_message;
                emitError(title, msg, CommunicationErrors.findDetailMessage(err));
                return;
            }
            state.postValue(copyWith(false, false));
            pendingOffer = null;
            event.postValue(new Event<>(new VpRequestEvent.ShowSuccess()));
        }, mainExecutor);
    }

    public void onBioAuthFailed() {
        cancelActiveSession();
        pendingOffer = null;
        emitAuthFailed();
    }

    public void onAuthCancelled() {
        cancelActiveSession();
        pendingOffer = null;
    }

    @Override
    protected void onCleared() {
        cancelActiveSession();
    }

    public void toggleClaim(@NonNull String claimKey) {
        VpRequestUiState cur = requireState();
        Set<String> next = new HashSet<>(cur.uncheckedClaimKeys);
        boolean wasChecked = !next.contains(claimKey);
        if (wasChecked) {
            next.add(claimKey);
            if (!warnedAtLeastOnce) {
                warnedAtLeastOnce = true;
                event.postValue(new Event<>(new VpRequestEvent.ShowWarn()));
            }
        } else {
            next.remove(claimKey);
        }
        state.setValue(cur.withUncheckedClaimKeys(next));
    }

    public void toggleGroupClaims(@NonNull List<String> groupClaimKeys) {
        if (groupClaimKeys.isEmpty()) return;
        VpRequestUiState cur = requireState();
        Set<String> next = new HashSet<>(cur.uncheckedClaimKeys);
        boolean wasChecked = !next.contains(groupClaimKeys.get(0));
        if (wasChecked) {
            next.addAll(groupClaimKeys);
            if (!warnedAtLeastOnce) {
                warnedAtLeastOnce = true;
                event.postValue(new Event<>(new VpRequestEvent.ShowWarn()));
            }
        } else {
            next.removeAll(groupClaimKeys);
        }
        state.setValue(cur.withUncheckedClaimKeys(next));
    }

    public void toggleReveal(@NonNull String attrLabel) {
        VpRequestUiState cur = requireState();
        Set<String> next = new HashSet<>(cur.revealedAttrLabels);
        if (next.contains(attrLabel)) next.remove(attrLabel);
        else next.add(attrLabel);
        state.setValue(cur.withRevealedAttrLabels(next));
    }

    public void toggleSection(@NonNull String sectionKey) {
        VpRequestUiState cur = requireState();
        Set<String> next = new HashSet<>(cur.collapsedSections);
        if (next.contains(sectionKey)) next.remove(sectionKey);
        else next.add(sectionKey);
        state.setValue(cur.withCollapsedSections(next));
    }

    public void toggleGroup(@NonNull String groupKey) {
        VpRequestUiState cur = requireState();
        Set<String> next = new HashSet<>(cur.expandedGroups);
        if (next.contains(groupKey)) next.remove(groupKey);
        else next.add(groupKey);
        state.setValue(cur.withExpandedGroups(next));
    }

    public void setReferentSelection(@NonNull String attrLabel,
                                     @NonNull String value,
                                     @NonNull String credentialId) {
        VpRequestUiState cur = requireState();
        Map<String, String> nextValues = new HashMap<>(cur.selectedReferents);
        Map<String, String> nextCredIds = new HashMap<>(cur.selectedReferentCredIds);
        nextValues.put(attrLabel, value);
        nextCredIds.put(attrLabel, credentialId);
        state.setValue(cur.withSelectedReferents(nextValues).withSelectedReferentCredIds(nextCredIds));
    }

    private void cancelActiveSession() {
        pendingPinForBio = null;
        switch (currentFlow) {
            case ZKP:    zkpRepo.cancelZkpPresentation(); break;
            case OID4VP: oid4vpRepo.cancelOid4vpPresentation(); break;
            case OPENDID_VC:
            default:     vpRepo.cancelVpPresentation(); break;
        }
    }

    private void replaceScenario(@NonNull VpScenario scenario) {
        VpRequestUiState cur = requireState();
        state.postValue(new VpRequestUiState(
                false, false, scenario,
                cur.uncheckedClaimKeys, cur.revealedAttrLabels,
                cur.collapsedSections, cur.expandedGroups,
                cur.selectedReferents, cur.selectedReferentCredIds));
    }

    private VpRequestUiState copyWith(boolean loading, boolean inFlight) {
        VpRequestUiState cur = requireState();
        return new VpRequestUiState(loading, inFlight, cur.scenario,
                cur.uncheckedClaimKeys, cur.revealedAttrLabels,
                cur.collapsedSections, cur.expandedGroups,
                cur.selectedReferents, cur.selectedReferentCredIds);
    }

    @NonNull
    private VpRequestUiState requireState() {
        VpRequestUiState s = state.getValue();
        return s != null ? s : VpRequestUiState.initialLoading();
    }

    private void emitInvalid(@Nullable Throwable err) {
        emitError(R.string.notice_vp_invalid_title, R.string.notice_vp_invalid_message,
                CommunicationErrors.findDetailMessage(err));
    }

    private void emitError(int titleRes, int messageRes) {
        emitError(titleRes, messageRes, null);
    }

    private void emitError(int titleRes, int messageRes, @Nullable String detailMessage) {
        state.postValue(copyWith(false, false));
        event.postValue(new Event<>(new VpRequestEvent.ShowError(
                titleRes, messageRes, detailMessage)));
    }

    private void emitAuthFailed() {
        cancelActiveSession();
        pendingOffer = null;
        emitError(R.string.notice_vp_auth_failed_title,
                R.string.notice_vp_auth_failed_message);
    }
}
