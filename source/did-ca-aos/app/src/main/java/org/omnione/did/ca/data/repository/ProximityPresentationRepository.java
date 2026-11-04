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

package org.omnione.did.ca.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutionException;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpVerifier;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.data.statuslist.CredentialStatusRefs;
import org.omnione.did.ca.protocol.ProximityProtocol;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.WifiAwareReadiness;
import org.omnione.did.proximity.sdk.core.ProximityException;
import org.omnione.did.proximity.sdk.core.engagement.BleTransferMode;
import org.omnione.did.proximity.sdk.holder.EngagementMethod;
import org.omnione.did.proximity.sdk.holder.HolderSession;
import org.omnione.did.proximity.sdk.holder.ProximityRequest;
import org.omnione.did.proximity.sdk.holder.TransferConfig;
import org.omnione.did.sdk.core.exception.WalletCoreErrorCode;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.oid4vc.MdocCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.MdocDeviceResponse;
import org.omnione.did.sdk.datamodel.oid4vc.MdocRequestedDocument;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class ProximityPresentationRepository {

    private static final String TAG = "ProximityPresentationRepo";

    public interface StateListener {
        void onReaderConnected();

        default void onEngagementDelivered(@NonNull EngagementMethod method) {
        }

        void onTerminated();

        void onError(Throwable t);
    }

    private static final AtomicInteger SESSION_SEQ = new AtomicInteger();

    private static final class ProximitySession {
        final int id = SESSION_SEQ.incrementAndGet();
        HolderSession holder;
        final CompletableFuture<ProximityRequest> requestFuture = new CompletableFuture<>();
        volatile ProximityRequest request;
        volatile Throwable failure;
        volatile byte[] deviceResponse;

        final Map<String, List<String>> claimKeyToCode = new LinkedHashMap<>();
        final Map<String, List<String>> allCodesByOptionId = new LinkedHashMap<>();
        final Map<String, MdocCredentialItem> byOptionId = new LinkedHashMap<>();
        final Map<String, MdocRequestedDocument> matchedByOptionId = new LinkedHashMap<>();
    }

    private final Context appContext;
    private final WalletGateway walletGateway;
    private final ProximityProtocol protocol;
    private final CredentialStatusRepository statusRepo;

    private final AtomicReference<ProximitySession> current = new AtomicReference<>();
    private volatile StateListener stateListener;

    @Inject
    public ProximityPresentationRepository(@ApplicationContext Context appContext,
                                           WalletGateway walletGateway,
                                           ProximityProtocol protocol,
                                           CredentialStatusRepository statusRepo) {
        this.appContext = appContext;
        this.walletGateway = walletGateway;
        this.protocol = protocol;
        this.statusRepo = statusRepo;
    }

    public void setStateListener(@Nullable StateListener listener) {
        AppLog.d(TAG, "setStateListener " + (listener == null ? "null" : "set") + callSite());
        this.stateListener = listener;
    }

    @NonNull
    private static String callSite() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder(" from=");
        int printed = 0;
        for (StackTraceElement f : frames) {
            String cls = f.getClassName();
            if (cls.startsWith("java.lang.Thread") || cls.equals(ProximityPresentationRepository.class.getName())) {
                continue;
            }
            if (printed > 0) sb.append(" <- ");
            sb.append(f.getFileName()).append(':').append(f.getLineNumber());
            if (++printed == 3) break;
        }
        return sb.toString();
    }

    @NonNull
    private static String describe(@Nullable Throwable e) {
        if (e == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 512; t = t.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage());
            if (t.getCause() == t) break;
        }
        return sb.toString();
    }

    @NonNull
    public CompletableFuture<String> startEngagement(@NonNull BleTransferMode mode) {
        cancel();

        final ProximitySession s = new ProximitySession();
        final HolderSession holder;
        boolean wifiAware = WifiAwareReadiness.isReady(appContext);
        AppLog.d(TAG, "startEngagement mode=" + mode + " wifiAware=" + wifiAware);
        try {
            holder = HolderSession.create(appContext,
                    TransferConfig.ble(mode).withWifiAware(wifiAware));
        } catch (RuntimeException e) {
            return CaUtil.failedFuture(e);
        }
        s.holder = holder;
        if (!current.compareAndSet(null, s)) {
            holder.closeWithoutNotifying();
            return CaUtil.failedFuture(
                    new IllegalStateException("another proximity session was started concurrently"));
        }

        try {
            String qr = holder.deviceEngagementQr();
            AppLog.d(TAG, "deviceEngagement=" + qr);
            holder.start(new HolderSession.Listener() {
                @Override
                public void onReaderConnected() {
                    if (current.get() != s) {
                        AppLog.w(TAG, "onReaderConnected dropped, session#" + s.id + " is stale");
                        return;
                    }
                    AppLog.d(TAG, "onReaderConnected session#" + s.id);
                    StateListener l = stateListener;
                    if (l != null) l.onReaderConnected();
                }

                @Override
                public void onEngagementDelivered(@NonNull EngagementMethod method) {
                    if (current.get() != s) {
                        AppLog.w(TAG, "onEngagementDelivered dropped, session#" + s.id
                                + " is stale: " + method);
                        return;
                    }
                    AppLog.d(TAG, "onEngagementDelivered session#" + s.id + " method=" + method);
                    StateListener l = stateListener;
                    if (l != null) l.onEngagementDelivered(method);
                }

                @Override
                public void onRequest(@NonNull ProximityRequest request) {
                    if (current.get() != s) {
                        AppLog.w(TAG, "onRequest dropped, session#" + s.id + " is stale");
                        return;
                    }
                    AppLog.d(TAG, "onRequest session#" + s.id
                            + " docType=" + request.docType
                            + " sessionTranscript.length=" + request.sessionTranscript.length);
                    s.request = request;
                    s.requestFuture.complete(request);
                }

                @Override
                public void onResponseSent() {
                    AppLog.d(TAG, "onResponseSent session#" + s.id
                            + (current.get() == s ? "" : " (stale)"));
                }

                @Override
                public void onError(@NonNull ProximityException error) {
                    if (current.get() != s) {
                        AppLog.w(TAG, "onError dropped, session#" + s.id + " is stale: "
                                + describe(error));
                        return;
                    }
                    AppLog.e(TAG, "onError session#" + s.id + " -> " + describe(error), error);
                    s.failure = error;
                    current.compareAndSet(s, null);
                    s.requestFuture.completeExceptionally(error);
                    StateListener l = stateListener;
                    if (l != null) l.onError(error);
                }

                @Override
                public void onTerminated() {
                    if (current.get() != s) {
                        AppLog.w(TAG, "onTerminated dropped, session#" + s.id + " is stale");
                        return;
                    }
                    AppLog.w(TAG, "onTerminated session#" + s.id
                            + " deviceResponse=" + (s.deviceResponse == null
                                    ? "null" : s.deviceResponse.length + "B"));
                    current.compareAndSet(s, null);
                    s.requestFuture.completeExceptionally(
                            new ProximitySessionEndedException("the verifier ended the session"));
                    StateListener l = stateListener;
                    if (l != null) l.onTerminated();
                }
            });
            return CompletableFuture.completedFuture(qr);
        } catch (RuntimeException e) {
            current.compareAndSet(s, null);
            holder.closeWithoutNotifying();
            return CaUtil.failedFuture(e);
        }
    }

    @NonNull
    public CompletableFuture<ProximityRequest> awaitRequest() {
        ProximitySession s = current.get();
        if (s == null) {
            return CaUtil.failedFuture(
                    new IllegalStateException("no proximity session"));
        }
        return s.requestFuture;
    }

    @NonNull
    public CompletableFuture<VpScenario> prepareProximityPresentation(
            @NonNull ProximityRequest request) {
        final ProximitySession s = current.get();
        if (s == null) {
            return CaUtil.failedFuture(
                    new IllegalStateException("no proximity session"));
        }
        return presentToken().thenCompose(token ->
                protocol.matchMdocRequest(token, request.deviceRequest)
                        .handle(ProximityPresentationRepository::mapMatchResult)
                        .thenApplyAsync(matched -> buildScenario(s, request, token, matched)));
    }

    @NonNull
    public CompletableFuture<Void> signProximityWithPin(@NonNull String pin,
                                                        @NonNull String optionId,
                                                        @NonNull Set<String> includedClaimKeys) {
        return doSign(pin, optionId, includedClaimKeys);
    }

    @NonNull
    public CompletableFuture<Void> signProximityWithBio(@NonNull String optionId,
                                                        @NonNull Set<String> includedClaimKeys) {
        return doSign(null, optionId, includedClaimKeys);
    }

    @NonNull
    public CompletableFuture<Void> submitProximityPresentation() {
        ProximitySession s = current.get();
        if (s == null || s.deviceResponse == null || s.holder == null) {
            String why = "submit guard 1: session=" + (s == null ? "null" : "#" + s.id)
                    + " deviceResponse=" + (s == null || s.deviceResponse == null
                            ? "null" : s.deviceResponse.length + "B")
                    + " holder=" + (s == null || s.holder == null ? "null" : "ok");
            AppLog.e(TAG, why);
            return CaUtil.failedFuture(new IllegalStateException("nothing to submit"));
        }
        AppLog.d(TAG, "submit session#" + s.id + " sending " + s.deviceResponse.length + "B");
        try {
            s.holder.sendResponse(s.deviceResponse);
        } catch (RuntimeException e) {
            AppLog.e(TAG, "submit guard 2: sendResponse threw -> " + describe(e), e);
            return CaUtil.failedFuture(e);
        }
        Throwable failure = s.failure;
        if (failure != null) {
            AppLog.e(TAG, "submit guard 3: session#" + s.id + " already failed -> "
                    + describe(failure), failure);
            return CaUtil.failedFuture(failure);
        }
        if (current.get() != s) {
            ProximitySession now = current.get();
            AppLog.e(TAG, "submit guard 4: session#" + s.id + " replaced by "
                    + (now == null ? "null" : "#" + now.id));
            return CaUtil.failedFuture(
                    new IllegalStateException("proximity session is no longer current"));
        }
        AppLog.d(TAG, "submit session#" + s.id + " handed off to transport");
        return CompletableFuture.completedFuture(null);
    }

    public void cancel() {
        ProximitySession s = current.getAndSet(null);
        AppLog.d(TAG, "cancel session=" + (s == null ? "none" : "#" + s.id) + callSite());
        if (s == null) return;
        s.requestFuture.completeExceptionally(
                new IllegalStateException("proximity session cancelled"));
        if (s.holder != null) {
            s.holder.terminate();
        }
    }

    @NonNull
    private CompletableFuture<Void> doSign(@Nullable String passcode,
                                           @NonNull String optionId,
                                           @NonNull Set<String> includedClaimKeys) {
        final ProximitySession s = current.get();
        if (s == null || s.request == null) {
            return CaUtil.failedFuture(
                    new IllegalStateException("no proximity session"));
        }
        MdocCredentialItem item = s.byOptionId.get(optionId);
        MdocRequestedDocument matched = s.matchedByOptionId.get(optionId);
        if (item == null || matched == null) {
            return CaUtil.failedFuture(
                    new IllegalStateException("unknown option: " + optionId));
        }

        Set<String> includedCodes = new HashSet<>();
        for (Map.Entry<String, List<String>> e : s.claimKeyToCode.entrySet()) {
            if (includedClaimKeys.contains(e.getKey())) {
                includedCodes.addAll(e.getValue());
            }
        }
        List<String> allCodes = s.allCodesByOptionId.get(optionId);
        List<String> kept = new ArrayList<>();
        if (allCodes != null) {
            for (String c : allCodes) {
                if (includedCodes.contains(c) && !kept.contains(c)) {
                    kept.add(c);
                }
            }
        }
        if (kept.isEmpty()) {
            return CaUtil.failedFuture(
                    new IllegalStateException("nothing to disclose for option: " + optionId));
        }

        final List<MdocRequestedDocument> selected = Collections.singletonList(
                new MdocRequestedDocument(matched.getDocRequestIndex(), matched.getDocType(),
                        matched.getCredentialId(), kept,
                        matched.getIntentToRetain(), matched.getMissing()));
        final byte[] deviceRequest = s.request.deviceRequest;
        final byte[] transcript = s.request.sessionTranscript;
        AppLog.d(TAG, "doSign credentialId=" + matched.getCredentialId()
                + " kid=" + item.getKid()
                + " authType=" + (Constants.KEY_ID_BIO.equals(item.getKid()) ? "BIO" : "PIN")
                + " docRequestIndex=" + matched.getDocRequestIndex()
                + " docType=" + matched.getDocType()
                + " claimCodes=" + matched.getClaimCodes()
                + " kept=" + kept
                + " allCodes=" + allCodes);
        return presentToken()
                .thenCompose(token -> protocol.createDeviceResponse(
                        token, deviceRequest, transcript, selected, passcode))
                .thenAccept(response -> {
                    byte[] bytes = response == null ? null : response.getResponse();
                    if (current.get() != s) {
                        ProximitySession now = current.get();
                        AppLog.e(TAG, "doSign guard: session#" + s.id + " replaced by "
                                + (now == null ? "null" : "#" + now.id)
                                + ", discarding " + (bytes == null ? "null" : bytes.length + "B"));
                        throw new CompletionException(new IllegalStateException(
                                "proximity session is no longer current"));
                    }
                    AppLog.d(TAG, "doSign stored deviceResponse session#" + s.id
                            + " " + (bytes == null ? "null" : bytes.length + "B")
                            + " authMethods="
                            + (response == null ? "null" : response.getAuthMethods()));
                    s.deviceResponse = bytes;
                });
    }

    @NonNull
    private VpScenario buildScenario(@NonNull ProximitySession s,
                                     @NonNull ProximityRequest request,
                                     @NonNull String token,
                                     @NonNull List<MdocRequestedDocument> matched) {
        Map<String, MdocCredentialItem> itemsById = new LinkedHashMap<>();
        for (MdocCredentialItem item : loadMdocItems(token)) {
            if (item != null && item.getId() != null && item.getMdoc() != null) {
                itemsById.put(item.getId(), item);
            }
        }

        Map<String, MdocCredentialItem> candidatesById = new LinkedHashMap<>();
        for (MdocRequestedDocument doc : matched) {
            MdocCredentialItem item = itemsById.get(doc.getCredentialId());
            if (item != null) {
                candidatesById.put(doc.getCredentialId(), item);
            }
        }
        Set<String> activeIds = new LinkedHashSet<>();
        for (MdocCredentialItem item : filterActive(new ArrayList<>(candidatesById.values()))) {
            activeIds.add(item.getId());
        }

        List<VpRequestedDoc> docs = new ArrayList<>(matched.size());
        boolean mustPin = false;
        boolean mustBio = false;
        for (MdocRequestedDocument doc : matched) {
            if (!activeIds.contains(doc.getCredentialId())) continue;
            MdocCredentialItem item = itemsById.get(doc.getCredentialId());
            String optionId = optionId(doc);
            if (Constants.KEY_ID_BIO.equals(item.getKid())) {
                mustBio = true;
            } else {
                mustPin = true;
            }
            MdocVpDocBuilder.Result result = MdocVpDocBuilder.build(optionId, item,
                    new LinkedHashSet<>(doc.getClaimCodes()));
            docs.add(result.doc);
            s.claimKeyToCode.putAll(result.claimKeyToCode);
            s.allCodesByOptionId.put(optionId, result.allCodes);
            s.byOptionId.put(optionId, item);
            s.matchedByOptionId.put(optionId, doc);
        }
        if (docs.isEmpty()) {
            throw new NoActiveCredentialException(
                    "No ACTIVE credential matches " + request.docType);
        }

        VerifyAuthType.VERIFY_AUTH_TYPE authType = mustPin
                ? VerifyAuthType.VERIFY_AUTH_TYPE.PIN
                : (mustBio ? VerifyAuthType.VERIFY_AUTH_TYPE.BIO
                           : VerifyAuthType.VERIFY_AUTH_TYPE.PIN);

        return VpScenario.proximity("proximity:" + request.docType,
                new VpVerifier("proximity", ""), docs, authType);
    }

    @NonNull
    private static String optionId(@NonNull MdocRequestedDocument doc) {
        return doc.getDocRequestIndex() + "#" + doc.getCredentialId();
    }

    @NonNull
    private static List<MdocRequestedDocument> mapMatchResult(
            @Nullable List<MdocRequestedDocument> matched, @Nullable Throwable error) {
        if (error == null) {
            return matched == null ? Collections.<MdocRequestedDocument>emptyList() : matched;
        }
        Throwable root = error;
        while (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof WalletCoreException
                && ((WalletCoreException) root).getErrorCode()
                        == WalletCoreErrorCode.ERR_CODE_PROXIMITY_NO_MATCHED_DOCUMENTS.getCode()) {
            throw new CompletionException(new NoActiveCredentialException(
                    "No stored mdoc answers the proximity request"));
        }
        throw error instanceof CompletionException
                ? (CompletionException) error : new CompletionException(error);
    }

    @NonNull
    private List<MdocCredentialItem> filterActive(@NonNull List<MdocCredentialItem> candidates) {
        if (candidates.isEmpty()) return candidates;

        Map<String, CredentialStatusRef> refById = new LinkedHashMap<>();
        for (MdocCredentialItem item : candidates) {
            if (refById.containsKey(item.getId())) continue;
            refById.put(item.getId(), CredentialStatusRefs.fromMdocItem(item));
        }

        Map<String, VcStatus> statuses;
        try {
            statuses = statusRepo.refreshSubset(new ArrayList<>(refById.values())).get();
        } catch (ExecutionException | InterruptedException | RuntimeException e) {
            AppLog.w(TAG, "status refresh failed, keeping every candidate: " + e);
            return candidates;
        }

        List<MdocCredentialItem> active = new ArrayList<>(candidates.size());
        for (MdocCredentialItem item : candidates) {
            VcStatus status = statuses.get(item.getId());
            if (status == null || status == VcStatus.ACTIVE) {
                active.add(item);
            }
        }
        return active;
    }

    private CompletableFuture<String> presentToken() {
        return walletGateway.createLocalWalletToken(
                WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP,
                CaUtil.getPackageName(appContext));
    }

    @NonNull
    private List<MdocCredentialItem> loadMdocItems(String token) {
        try {
            return walletGateway.getAllMdocOID4VCs(token).get();
        } catch (ExecutionException | InterruptedException | RuntimeException e) {
            AppLog.w(TAG, "failed to load mdoc credentials: " + e);
            return Collections.emptyList();
        }
    }
}
