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
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.network.CommunicationErrors;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.repository.NoActiveCredentialException;
import org.omnione.did.ca.data.repository.ProximitySessionEndedException;
import org.omnione.did.ca.data.repository.Oid4vpSubmissionRepository;
import org.omnione.did.ca.data.repository.ProximityPresentationRepository;
import org.omnione.did.ca.data.model.VerifyOfferEnvelope;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.repository.VpOfferParser;
import org.omnione.did.ca.data.repository.VpSubmissionRepository;
import org.omnione.did.ca.data.repository.ZkpSubmissionRepository;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.ProximityErrors;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class VpRequestViewModel extends ViewModel {

    private static final String TAG = "VpRequestVm";

    private final VpSubmissionRepository vpRepo;
    private final ZkpSubmissionRepository zkpRepo;
    private final Oid4vpSubmissionRepository oid4vpRepo;
    private final ProximityPresentationRepository proximityRepo;
    private final VpOfferParser parser;
    private final WalletGateway walletGateway;
    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<VpRequestUiState> state =
            new MutableLiveData<>(VpRequestUiState.initialLoading());
    private final MutableLiveData<Event<VpRequestEvent>> event = new MutableLiveData<>();

    @Nullable private VerifyOfferEnvelope pendingOffer;

    private enum Flow { OPENDID_VC, ZKP, OID4VP, PROXIMITY }
    private Flow currentFlow = Flow.OPENDID_VC;

    @Nullable private String pendingPinForBio;
    private final AtomicBoolean proximitySessionClosed = new AtomicBoolean(false);

    @Inject
    public VpRequestViewModel(VpSubmissionRepository vpRepo,
                              ZkpSubmissionRepository zkpRepo,
                              Oid4vpSubmissionRepository oid4vpRepo,
                              ProximityPresentationRepository proximityRepo,
                              VpOfferParser parser,
                              WalletGateway walletGateway,
                              BiometricRepository bioRepo,
                              @ApplicationContext Context appContext) {
        this.vpRepo = vpRepo;
        this.zkpRepo = zkpRepo;
        this.oid4vpRepo = oid4vpRepo;
        this.proximityRepo = proximityRepo;
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
                        emitPrepareFailure(err2);
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
                    emitPrepareFailure(err2);
                    return;
                }
                replaceScenario(scenario);
            }, mainExecutor);
        }, mainExecutor);
    }

    public void startProximity() {
        currentFlow = Flow.PROXIMITY;
        state.postValue(VpRequestUiState.initialLoading());
        proximitySessionClosed.set(false);
        proximityRepo.setStateListener(new ProximityPresentationRepository.StateListener() {
            @Override
            public void onReaderConnected() {
            }

            @Override
            public void onTerminated() {
                AppLog.w(TAG, "proximity onTerminated");
                onProximitySessionClosed(
                        new ProximitySessionEndedException("the verifier ended the session"));
            }

            @Override
            public void onError(Throwable t) {
                AppLog.e(TAG, "proximity onError", t);
                onProximitySessionClosed(t);
            }
        });
        proximityRepo.awaitRequest()
                .thenCompose(proximityRepo::prepareProximityPresentation)
                .whenCompleteAsync((scenario, error) -> {
                    if (error != null) {
                        AppLog.e(TAG, "prepareProximityPresentation failed", error);
                        if (proximitySessionClosed.compareAndSet(false, true)) {
                            cancelActiveSession();
                            emitPrepareFailure(error);
                        }
                        return;
                    }
                    replaceScenario(scenario);
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
                setInFlight(true);
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null || !Boolean.TRUE.equals(bio)) {
                        emitAuthFailed();
                        return;
                    }
                    setInFlight(false);
                    event.postValue(new Event<>(new VpRequestEvent.LaunchBio()));
                }, mainExecutor);
                break;
            case ANY:
            case PIN_OR_BIO:
                setInFlight(true);
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null) {
                        emitAuthFailed();
                        return;
                    }
                    setInFlight(false);
                    if (Boolean.TRUE.equals(bio)) {
                        event.postValue(new Event<>(new VpRequestEvent.LaunchAuthMethodChooser()));
                    } else {
                        event.postValue(new Event<>(new VpRequestEvent.LaunchPin()));
                    }
                }, mainExecutor);
                break;
            case PIN_AND_BIO:
                setInFlight(true);
                walletGateway.isSavedKey(Constants.KEY_ID_BIO).whenCompleteAsync((bio, err) -> {
                    if (err != null || !Boolean.TRUE.equals(bio)) {
                        emitError(R.string.notice_vp_bio_not_registered_title,
                                R.string.notice_vp_bio_not_registered_message);
                        return;
                    }
                    setInFlight(false);
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

        if (currentFlow != Flow.OID4VP && currentFlow != Flow.PROXIMITY && pendingOffer == null) {
            cancelActiveSession();
            setInFlight(false);
            return;
        }
        VpRequestUiState cur = requireState();

        if (cur.scenario != null
                && cur.scenario.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO
                && pin != null
                && pendingPinForBio == null) {
            pendingPinForBio = pin;
            setInFlight(false);
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

        state.setValue(copyWith(true, true));

        CompletableFuture<Void> signFuture;
        CompletableFuture<Void> submitFuture;
        switch (currentFlow) {
            case ZKP:

                signFuture = (pinForSign == null)
                        ? zkpRepo.signZkpWithBio(cur.selectedReferents,
                                cur.selectedReferentCredIds, cur.selectedPredicateCredIds, cur.revealedAttrLabels)
                        : zkpRepo.signZkpWithPin(pinForSign, cur.selectedReferents,
                                cur.selectedReferentCredIds, cur.selectedPredicateCredIds, cur.revealedAttrLabels);
                submitFuture = signFuture.thenCompose(v -> zkpRepo.submitZkpPresentation());
                break;
            case OID4VP: {

                String optionId = cur.selectedOptionId != null ? cur.selectedOptionId : "";
                Set<String> included = cur.submissionClaimKeys();
                signFuture = (pinForSign == null)
                        ? oid4vpRepo.signOid4vpWithBio(optionId, included)
                        : oid4vpRepo.signOid4vpWithPin(pinForSign, optionId, included);
                submitFuture = signFuture.thenCompose(v -> oid4vpRepo.submitOid4vpPresentation());
                break;
            }
            case PROXIMITY: {

                String optionId = cur.selectedOptionId != null ? cur.selectedOptionId : "";
                Set<String> included = cur.submissionClaimKeys();
                signFuture = (pinForSign == null)
                        ? proximityRepo.signProximityWithBio(optionId, included)
                        : proximityRepo.signProximityWithPin(pinForSign, optionId, included);
                submitFuture = signFuture.thenCompose(v -> proximityRepo.submitProximityPresentation());
                break;
            }
            case OPENDID_VC:
            default: {

                String optionId = cur.selectedOptionId != null ? cur.selectedOptionId : "";
                Set<String> included = cur.submissionClaimKeys();
                signFuture = (pinForSign == null)
                        ? vpRepo.signVpWithBio(optionId, included)
                        : vpRepo.signVpWithPin(pinForSign, optionId, included);
                submitFuture = signFuture.thenCompose(v -> vpRepo.submitVpPresentation());
                break;
            }
        }

        submitFuture.whenCompleteAsync((v, err) -> {
            if (err != null) {
                if (currentFlow == Flow.PROXIMITY) {
                    AppLog.e(TAG, "proximity submitFuture failed", err);
                    if (proximitySessionClosed.compareAndSet(false, true)) {
                        emitSubmitFailure(err);
                    }
                    return;
                }
                emitSubmitFailure(err);
                return;
            }
            if (currentFlow == Flow.PROXIMITY) {
                if (!proximitySessionClosed.compareAndSet(false, true)) {
                    return;
                }
            }
            state.postValue(copyWith(false, false));
            pendingOffer = null;
            event.postValue(new Event<>(new VpRequestEvent.ShowSuccess()));
        }, mainExecutor);
    }

    private void emitSubmitFailure(@Nullable Throwable err) {
        AppLog.e(TAG, "submit failed flow=" + currentFlow, err);
        cancelActiveSession();
        pendingOffer = null;
        int title = CaUtil.isAuthError(err)
                ? R.string.notice_vp_auth_failed_title
                : R.string.notice_vp_failed_title;
        int msg = CaUtil.isAuthError(err)
                ? R.string.notice_vp_auth_failed_message
                : R.string.notice_vp_failed_message;
        emitError(title, msg, CommunicationErrors.findDetailMessage(err));
    }

    private void onProximitySessionClosed(@Nullable Throwable t) {
        if (currentFlow != Flow.PROXIMITY) {
            AppLog.w(TAG, "proximity session closed ignored, flow=" + currentFlow);
            return;
        }
        if (!proximitySessionClosed.compareAndSet(false, true)) {
            AppLog.w(TAG, "proximity session closed ignored, already closed");
            return;
        }
        AppLog.e(TAG, "proximity session closed", t);
        int messageRes = ProximityErrors.messageFor(t);
        if (messageRes == ProximityErrors.NOT_A_SESSION_ERROR) {
            emitSubmitFailure(t);
            return;
        }
        cancelActiveSession();
        pendingOffer = null;
        emitError(R.string.notice_vp_failed_title, messageRes);
    }

    public void onBioAuthFailed() {
        cancelActiveSession();
        pendingOffer = null;
        emitAuthFailed();
    }

    public void onAuthCancelled() {
        cancelActiveSession();
        pendingOffer = null;
        if (currentFlow == Flow.PROXIMITY) {
            if (proximitySessionClosed.compareAndSet(false, true)) {
                emitSubmitFailure(new IllegalStateException("proximity session terminated"));
            }
            return;
        }
        setInFlight(false);
    }

    @Override
    protected void onCleared() {
        cancelActiveSession();
    }

    public void toggleClaim(@NonNull String claimKey) {
        VpRequestUiState cur = requireState();
        Set<String> next = new LinkedHashSet<>(cur.checkedClaimKeys);
        if (!next.remove(claimKey)) {
            next.add(claimKey);
        }
        state.setValue(cur.withCheckedClaimKeys(next));
    }

    public void toggleGroupClaims(@NonNull List<String> groupClaimKeys) {
        if (groupClaimKeys.isEmpty()) return;
        VpRequestUiState cur = requireState();
        Set<String> next = new LinkedHashSet<>(cur.checkedClaimKeys);
        if (next.contains(groupClaimKeys.get(0))) {
            next.removeAll(groupClaimKeys);
        } else {
            next.addAll(groupClaimKeys);
        }
        state.setValue(cur.withCheckedClaimKeys(next));
    }

    public void toggleSelectAll(@NonNull String optionId) {
        VpRequestUiState cur = requireState();
        VpRequestedDoc doc = findCandidate(cur, optionId);
        if (doc == null) return;
        List<String> optionalKeys = VpRequestUiState.optionalClaimKeys(doc);
        if (optionalKeys.isEmpty()) return;

        Set<String> next = new LinkedHashSet<>(cur.checkedClaimKeys);
        if (cur.allOptionalChecked(doc)) {
            next.removeAll(optionalKeys);
        } else {
            next.addAll(optionalKeys);
        }
        state.setValue(cur.withCheckedClaimKeys(next));
    }

    public void selectOption(@NonNull String optionId) {
        VpRequestUiState cur = requireState();
        if (findCandidate(cur, optionId) == null) return;

        boolean sameOption = optionId.equals(cur.selectedOptionId);
        Set<String> checked = sameOption ? cur.checkedClaimKeys : Collections.emptySet();
        state.setValue(new VpRequestUiState(cur.loading, cur.inFlight, cur.scenario,
                checked, cur.revealedAttrLabels,
                Collections.singleton(optionId), cur.collapsedGroupKeys,
                cur.selectedReferents, cur.selectedReferentCredIds, cur.selectedPredicateCredIds, optionId));
    }

    public void toggleReveal(@NonNull String attrLabel) {
        VpRequestUiState cur = requireState();
        Set<String> next = new LinkedHashSet<>(cur.revealedAttrLabels);
        if (next.contains(attrLabel)) next.remove(attrLabel);
        else next.add(attrLabel);
        state.setValue(cur.withRevealedAttrLabels(next));
    }

    public void toggleOption(@NonNull String optionId) {
        VpRequestUiState cur = requireState();
        Set<String> next = new LinkedHashSet<>(cur.expandedOptionIds);
        if (!next.remove(optionId)) next.add(optionId);
        state.setValue(cur.withExpandedOptionIds(next));
    }

    public void toggleGroup(@NonNull String groupKey) {
        VpRequestUiState cur = requireState();
        Set<String> next = new LinkedHashSet<>(cur.collapsedGroupKeys);
        if (!next.remove(groupKey)) next.add(groupKey);
        state.setValue(cur.withCollapsedGroupKeys(next));
    }

    @Nullable
    private static VpRequestedDoc findCandidate(@NonNull VpRequestUiState state,
                                                @NonNull String optionId) {
        if (state.scenario == null) return null;
        for (VpRequestedDoc doc : state.scenario.candidates) {
            if (doc.docId.equals(optionId)) return doc;
        }
        return null;
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

    public void setPredicateSelection(@NonNull String referentKey, @NonNull String credentialId) {
        VpRequestUiState cur = requireState();
        Map<String, String> next = new HashMap<>(cur.selectedPredicateCredIds);
        next.put(referentKey, credentialId);
        state.setValue(cur.withSelectedPredicateCredIds(next));
    }

    private void cancelActiveSession() {
        pendingPinForBio = null;
        switch (currentFlow) {
            case ZKP:    zkpRepo.cancelZkpPresentation(); break;
            case OID4VP: oid4vpRepo.cancelOid4vpPresentation(); break;
            case PROXIMITY:
                proximityRepo.setStateListener(null);
                proximityRepo.cancel();
                break;
            case OPENDID_VC:
            default:     vpRepo.cancelVpPresentation(); break;
        }
    }

    private void replaceScenario(@NonNull VpScenario scenario) {
        VpRequestUiState cur = requireState();
        String firstId = scenario.candidates.isEmpty() ? null : scenario.candidates.get(0).docId;
        Set<String> expanded = (firstId != null && scenario.candidates.size() == 1)
                ? Collections.singleton(firstId)
                : Collections.emptySet();
        state.postValue(new VpRequestUiState(
                false, false, scenario,
                Collections.emptySet(), cur.revealedAttrLabels,
                expanded, Collections.emptySet(),
                cur.selectedReferents, cur.selectedReferentCredIds, cur.selectedPredicateCredIds, firstId));
    }

    private void setInFlight(boolean value) {
        state.setValue(copyWith(false, value));
    }

    private VpRequestUiState copyWith(boolean loading, boolean inFlight) {
        VpRequestUiState cur = requireState();
        return new VpRequestUiState(loading, inFlight, cur.scenario,
                cur.checkedClaimKeys, cur.revealedAttrLabels,
                cur.expandedOptionIds, cur.collapsedGroupKeys,
                cur.selectedReferents, cur.selectedReferentCredIds, cur.selectedPredicateCredIds, cur.selectedOptionId);
    }

    @NonNull
    private VpRequestUiState requireState() {
        VpRequestUiState s = state.getValue();
        return s != null ? s : VpRequestUiState.initialLoading();
    }

    private void emitPrepareFailure(@Nullable Throwable err) {
        Throwable root = err;
        while (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof NoActiveCredentialException) {
            emitError(R.string.notice_vp_no_candidate_title,
                    R.string.notice_vp_no_candidate_message);
            return;
        }
        int messageRes = ProximityErrors.messageFor(root);
        if (messageRes != ProximityErrors.NOT_A_SESSION_ERROR) {
            emitError(R.string.notice_vp_failed_title, messageRes);
            return;
        }
        emitInvalid(err);
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
