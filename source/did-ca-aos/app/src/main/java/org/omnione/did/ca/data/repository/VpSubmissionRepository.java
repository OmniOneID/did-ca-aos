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
import org.omnione.did.ca.data.model.VpClaimKeys;
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
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.data.statuslist.CredentialStatusRefs;
import org.omnione.did.ca.util.VcSchemaTitle;
import org.omnione.did.sdk.core.vcmanager.datamodel.ClaimInfo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.profile.ProviderDetail;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

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
    private final CredentialStatusRepository statusRepo;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private volatile SdkSession session;

    private static final class SdkSession {
        final String offerId;
        String txId;
        String hWalletToken;
        VerifyProfile profile;
        ReturnEncVP returnEncVP;

        final LinkedHashMap<String, Candidate> candidatesById = new LinkedHashMap<>();

        SdkSession(String offerId) {
            this.offerId = offerId;
        }
    }

    private static final class Candidate {
        final String vcId;
        final List<String> displayCodes;
        final Set<String> requiredCodes;

        Candidate(String vcId, List<String> displayCodes, Set<String> requiredCodes) {
            this.vcId = vcId;
            this.displayCodes = displayCodes;
            this.requiredCodes = requiredCodes;
        }
    }

    @Inject
    public VpSubmissionRepository(@ApplicationContext Context appContext,
                                         NetworkManager net,
                                         AppConfig config,
                                         WalletGateway walletGateway,
                                         UserIdentityRepository identity,
                                         CredentialStatusRepository statusRepo) {
        this.appContext = appContext;
        this.net = net;
        this.config = config;
        this.walletGateway = walletGateway;
        this.identity = identity;
        this.statusRepo = statusRepo;
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

                List<VerifiableCredential> matches = new ArrayList<>();
                for (VerifiableCredential vc : vcList) {
                    if (vc.getCredentialSchema() != null
                            && filterSchemaId.equals(vc.getCredentialSchema().getId())) {
                        matches.add(vc);
                    }
                }

                List<VerifiableCredential> active = filterActive(matches);
                if (active.isEmpty()) {
                    throw new NoActiveCredentialException(
                            "No ACTIVE credential for filter: " + filterSchemaId);
                }

                for (VerifiableCredential vc : active) {
                    Policy policy = resolvePolicy(filterSchema, vc);
                    s.candidatesById.put(vc.getId(),
                            new Candidate(vc.getId(), policy.displayCodes, policy.requiredCodes));
                }

                return buildScenario(s, s.profile, active);
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private List<VerifiableCredential> filterActive(List<VerifiableCredential> matches)
            throws Exception {
        if (matches.isEmpty()) return matches;
        List<CredentialStatusRef> refs = new ArrayList<>(matches.size());
        for (VerifiableCredential vc : matches) {
            refs.add(CredentialStatusRefs.fromOpenDidVc(vc));
        }
        Map<String, VcStatus> statuses = statusRepo.refreshSubset(refs).get();
        List<VerifiableCredential> active = new ArrayList<>(matches.size());
        for (VerifiableCredential vc : matches) {
            VcStatus status = statuses.get(vc.getId());
            if (status == null || status == VcStatus.ACTIVE) {
                active.add(vc);
            }
        }
        return active;
    }

    private VpScenario buildScenario(SdkSession s,
                                     VerifyProfile profile,
                                     List<VerifiableCredential> candidates) {
        ProviderDetail verifierDetail = profile.getProfile().getVerifier();
        VpVerifier verifier = new VpVerifier(
                safe(verifierDetail.getDID()),
                safe(verifierDetail.getName()));

        List<VpRequestedDoc> docs = new ArrayList<>(candidates.size());
        for (VerifiableCredential vc : candidates) {
            docs.add(buildDoc(s.candidatesById.get(vc.getId()), vc));
        }

        VerifyAuthType.VERIFY_AUTH_TYPE authType;
        try {
            authType = profile.getProfile().getProcess().getAuthType();
        } catch (RuntimeException e) {
            authType = null;
        }
        if (authType == null) {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.PIN;
        }

        return VpScenario.openDidVc(s.offerId, verifier, docs, authType);
    }

    private static VpRequestedDoc buildDoc(Candidate candidate, VerifiableCredential vc) {
        String schemaId = (vc.getCredentialSchema() != null)
                ? safe(vc.getCredentialSchema().getId()) : "";
        String docTitle = VcSchemaTitle.resolve(schemaId);

        List<VpDocEntry> entries = new ArrayList<>();
        if (vc.getCredentialSubject() != null
                && vc.getCredentialSubject().getClaims() != null) {
            for (String code : candidate.displayCodes) {
                for (Claim c : vc.getCredentialSubject().getClaims()) {
                    if (code.equals(c.getCode())) {
                        String label = safe(c.getCaption());
                        if (label.isEmpty()) label = code;
                        String value = c.getValue() != null ? c.getValue() : "";
                        entries.add(VpDocEntry.of(new VpRequestedClaim(
                                label, value, code, candidate.requiredCodes.contains(code))));
                        break;
                    }
                }
            }
        }
        return new VpRequestedDoc(candidate.vcId, docTitle, entries);
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

        Set<String> required = new LinkedHashSet<>();
        List<String> requiredClaims = filterSchema.getRequiredClaims();
        if (requiredClaims != null) {
            for (String code : requiredClaims) {
                if (candidateSet.contains(code)) required.add(code);
            }
        }

        List<String> displayClaims = filterSchema.getDisplayClaims();
        if (displayClaims == null || displayClaims.isEmpty()) {
            return new Policy(candidates, required);
        }

        List<String> display = new ArrayList<>(required);
        for (String code : intersectOrdered(displayClaims, candidateSet)) {
            if (!display.contains(code)) display.add(code);
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
                                                 @NonNull String selectedOptionId,
                                                 @NonNull Set<String> includedClaimKeys) {
        SdkSession s = session;
        if (s == null) return failedSession();
        return doSign(s, pin, selectedOptionId, includedClaimKeys);
    }

        public CompletableFuture<Void> signVpWithBio(@NonNull String selectedOptionId,
                                                 @NonNull Set<String> includedClaimKeys) {
        SdkSession s = session;
        if (s == null) return failedSession();

        return doSign(s, "", selectedOptionId, includedClaimKeys);
    }

    private CompletableFuture<Void> doSign(SdkSession s, String passcode,
                                           String selectedOptionId,
                                           Set<String> includedClaimKeys) {
        Candidate candidate = s.candidatesById.get(selectedOptionId);
        if (candidate == null) {
            return failedSession();
        }

        Set<String> submit = new LinkedHashSet<>(candidate.requiredCodes);
        for (String code : candidate.displayCodes) {
            if (includedClaimKeys.contains(VpClaimKeys.flat(candidate.vcId, code))) {
                submit.add(code);
            }
        }

        List<ClaimInfo> claimInfos = Collections.singletonList(
                new ClaimInfo(candidate.vcId, new ArrayList<>(submit)));
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
