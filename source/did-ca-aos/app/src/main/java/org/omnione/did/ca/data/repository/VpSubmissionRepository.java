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

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpVerifier;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.network.protocol.RequestVerifyProfileOp;
import org.omnione.did.ca.data.network.protocol.RequestVerifyVpOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.VcSchemaTitle;
import org.omnione.did.sdk.core.vcmanager.datamodel.ClaimInfo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.profile.ProviderDetail;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class VpSubmissionRepository {

    private final Context appContext;
    private final NetworkManager net;
    private final AppConfig config;
    private final WalletGateway walletGateway;
    private final UserIdentityRepository identity;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private volatile SdkSession session;

    private static final class SdkSession {
        final String offerId;
        String txId;
        String hWalletToken;
        VerifyProfile profile;
        String vcId;

        List<String> displayCodes = Collections.emptyList();

        Set<String> requiredCodes = Collections.emptySet();
        ReturnEncVP returnEncVP;

        SdkSession(String offerId) {
            this.offerId = offerId;
        }
    }

    @Inject
    public VpSubmissionRepository(@ApplicationContext Context appContext,
                                         NetworkManager net,
                                         AppConfig config,
                                         WalletGateway walletGateway,
                                         UserIdentityRepository identity) {
        this.appContext = appContext;
        this.net = net;
        this.config = config;
        this.walletGateway = walletGateway;
        this.identity = identity;
    }

        public CompletableFuture<VpScenario> prepareVpPresentation(@NonNull String offerId,
                                                               @NonNull String txId) {
        SdkSession s = new SdkSession(offerId);
        s.txId = txId;
        session = s;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String verifier = config.verifierUrl();

                RequestVerifyProfileOp.Result profileResult = RequestVerifyProfileOp.call(
                        net, appContext, verifier, offerId, s.txId);
                s.txId = profileResult.txId;
                s.profile = profileResult.profile;

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP).get();

                List<VerifiableCredential> vcList =
                        walletGateway.getAllCredentials(s.hWalletToken).get();

                VerifyProfile.CredentialSchema filterSchema =
                        s.profile.getProfile().getFilter().getCredentialSchemas().get(0);
                String filterSchemaId = filterSchema.getId();

                VerifiableCredential matched = null;
                for (VerifiableCredential vc : vcList) {
                    if (vc.getCredentialSchema() != null
                            && filterSchemaId.equals(vc.getCredentialSchema().getId())) {
                        matched = vc;
                        break;
                    }
                }
                if (matched == null) {
                    throw new IllegalStateException("No matching credential for filter: "
                            + filterSchemaId);
                }

                Policy policy = resolvePolicy(filterSchema, matched);
                s.vcId = matched.getId();
                s.displayCodes = policy.displayCodes;
                s.requiredCodes = policy.requiredCodes;

                return buildScenario(s, s.profile, matched);
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private VpScenario buildScenario(SdkSession s,
                                     VerifyProfile profile,
                                     VerifiableCredential vc) {
        ProviderDetail verifierDetail = profile.getProfile().getVerifier();
        VpVerifier verifier = new VpVerifier(
                safe(verifierDetail.getDID()),
                safe(verifierDetail.getName()));

        String schemaId = (vc.getCredentialSchema() != null)
                ? safe(vc.getCredentialSchema().getId()) : "";
        String docTitle = VcSchemaTitle.resolve(schemaId);

        List<VpDocEntry> entries = new ArrayList<>();
        if (vc.getCredentialSubject() != null
                && vc.getCredentialSubject().getClaims() != null) {
            for (String code : s.displayCodes) {
                for (Claim c : vc.getCredentialSubject().getClaims()) {
                    if (code.equals(c.getCode())) {
                        String label = safe(c.getCaption());
                        if (label.isEmpty()) label = code;
                        String value = c.getValue() != null ? c.getValue() : "";
                        entries.add(VpDocEntry.of(new VpRequestedClaim(
                                label, value, code, s.requiredCodes.contains(code))));
                        break;
                    }
                }
            }
        }

        VpRequestedDoc doc = new VpRequestedDoc(s.vcId, docTitle, entries);

        VerifyAuthType.VERIFY_AUTH_TYPE authType;
        try {
            authType = profile.getProfile().getProcess().getAuthType();
        } catch (Exception e) {
            authType = null;
        }
        if (authType == null) {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.PIN;
        }

        return VpScenario.openDidVc(s.offerId, verifier, Collections.singletonList(doc), authType);
    }

    private static final class Policy {
        final List<String> displayCodes;
        final Set<String> requiredCodes;
        Policy(List<String> displayCodes, Set<String> requiredCodes) {
            this.displayCodes = displayCodes;
            this.requiredCodes = requiredCodes;
        }
    }

    private static Policy resolvePolicy(VerifyProfile.CredentialSchema filterSchema,
                                        VerifiableCredential vc) {
        List<String> candidates = new ArrayList<>();
        if (vc.getCredentialSubject() != null && vc.getCredentialSubject().getClaims() != null) {
            for (Claim c : vc.getCredentialSubject().getClaims()) {
                candidates.add(c.getCode());
            }
        }
        Set<String> candidateSet = new HashSet<>(candidates);

        if (filterSchema.isPresentAll()) {
            return new Policy(candidates, new LinkedHashSet<>(candidates));
        }

        List<String> displayClaims = filterSchema.getDisplayClaims();
        List<String> display = (displayClaims != null && !displayClaims.isEmpty())
                ? intersectOrdered(displayClaims, candidateSet)
                : candidates;

        Set<String> required = new HashSet<>();
        List<String> requiredClaims = filterSchema.getRequiredClaims();
        if (requiredClaims != null) {
            for (String code : requiredClaims) {
                if (candidateSet.contains(code)) required.add(code);
            }
        }
        return new Policy(display, required);
    }

    private static List<String> intersectOrdered(List<String> src, Set<String> allowed) {
        List<String> out = new ArrayList<>();
        for (String code : src) {
            if (allowed.contains(code)) out.add(code);
        }
        return out;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

        public CompletableFuture<Void> signVpWithPin(@NonNull String pin,
                                                 @NonNull Set<String> uncheckedClaimKeys) {
        SdkSession s = session;
        if (s == null) return failedSession();
        return doSign(s, pin, uncheckedClaimKeys);
    }

        public CompletableFuture<Void> signVpWithBio(@NonNull Set<String> uncheckedClaimKeys) {
        SdkSession s = session;
        if (s == null) return failedSession();

        return doSign(s, "", uncheckedClaimKeys);
    }

    private CompletableFuture<Void> doSign(SdkSession s, String passcode,
                                           Set<String> uncheckedClaimKeys) {

        Set<String> submit = new LinkedHashSet<>(s.requiredCodes);
        for (String code : s.displayCodes) {
            if (!uncheckedClaimKeys.contains(s.vcId + ":" + code)) {
                submit.add(code);
            }
        }

        List<ClaimInfo> claimInfos = Collections.singletonList(
                new ClaimInfo(s.vcId, new ArrayList<>(submit)));
        return walletGateway.createEncVp(s.hWalletToken, claimInfos, s.profile,
                        config.apiGwUrl(), passcode)
                .thenAccept(result -> s.returnEncVP = result);
    }

        public CompletableFuture<Void> submitVpPresentation() {
        SdkSession s = session;
        if (s == null) return failedSession();
        return CompletableFuture.runAsync(() -> {
            try {
                RequestVerifyVpOp.call(net, appContext, config.verifierUrl(),
                        s.txId, s.returnEncVP.getEncVp(), s.returnEncVP.getAccE2e());
                session = null;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

        public void cancelVpPresentation() {
        session = null;
    }

    private static <T> CompletableFuture<T> failedSession() {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException("no session"));
        return f;
    }
}
