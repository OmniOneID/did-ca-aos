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

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedClaimGroup;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpVerifier;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.protocol.OID4VPProtocol;
import org.omnione.did.ca.util.VcSchemaTitle;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.core.oid4vc.format.sdjwt.SdJwtParser;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.oid4vc.AuthorizationRequest;
import org.omnione.did.sdk.datamodel.oid4vc.CredentialFormat;
import org.omnione.did.sdk.datamodel.oid4vc.MatchedCredential;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwt;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwtCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.dcql.CredentialQuery;
import org.omnione.did.sdk.datamodel.oid4vc.dcql.DCQLQuery;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
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

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class Oid4vpSubmissionRepository {

    private final OID4VPProtocol protocol;
    private final WalletGateway walletGateway;
    private final WalletApi walletApi;
    private final NetworkManager net;
    private final AppConfig config;
    private final UserIdentityRepository identity;
    private final Context appContext;
    private volatile SdkSession session;

    private static final class SdkSession {
        AuthorizationRequest authRequest;
        List<MatchedCredential> matched;
        byte[] responseBody;

        final LinkedHashMap<String, List<String>> claimKeyToCode = new LinkedHashMap<>();
        final Map<String, List<String>> allCodesByCredentialId = new LinkedHashMap<>();
    }

    private static final class TokenAndMatched {
        final String token;
        final List<MatchedCredential> matched;
        TokenAndMatched(String token, List<MatchedCredential> matched) {
            this.token = token;
            this.matched = matched;
        }
    }

    @Inject
    public Oid4vpSubmissionRepository(@ApplicationContext Context appContext,
                                      NetworkManager net,
                                      AppConfig config,
                                      WalletGateway walletGateway,
                                      UserIdentityRepository identity,
                                      WalletApi walletApi,
                                      OID4VPProtocol protocol) {
        this.walletGateway = walletGateway;
        this.walletApi = walletApi;
        this.net = net;
        this.config = config;
        this.identity = identity;
        this.appContext = appContext;
        this.protocol = protocol;
    }

    public CompletableFuture<VpScenario> prepareOid4vpPresentation(@NonNull String rawUri) {
        SdkSession s = new SdkSession();
        session = s;
        return protocol.getAuthorizationRequest(rawUri)
                .thenCompose(ar -> {
                    s.authRequest = ar;
                    return presentToken()
                            .thenCompose(token -> protocol.matchCredentials(token, ar)
                                    .thenApply(matched -> new TokenAndMatched(token, matched)));
                })
                .handleAsync((tm, t) -> {
                    if (t != null) {
                        session = null;
                        throw new CompletionException(t);
                    }
                    s.matched = tm.matched;
                    return buildScenario(s, tm.token);
                });
    }

    public CompletableFuture<Void> signOid4vpWithPin(@NonNull String pin,
                                                     @NonNull Set<String> uncheckedClaimKeys) {
        return doSign(pin, uncheckedClaimKeys);
    }

    public CompletableFuture<Void> signOid4vpWithBio(@NonNull Set<String> uncheckedClaimKeys) {
        return doSign(null, uncheckedClaimKeys);
    }

    private CompletableFuture<Void> doSign(@Nullable String passcode,
                                           @NonNull Set<String> uncheckedClaimKeys) {
        SdkSession s = session;
        if (s == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("prepareOid4vpPresentation() not called"));
        }

        Set<String> includedCodes = null;
        if (!s.claimKeyToCode.isEmpty()) {
            includedCodes = new HashSet<>();
            for (Map.Entry<String, List<String>> e : s.claimKeyToCode.entrySet()) {
                if (!uncheckedClaimKeys.contains(e.getKey())) {
                    includedCodes.addAll(e.getValue());
                }
            }
        }
        final List<MatchedCredential> toPresent =
                applyDisclosure(s.matched, includedCodes, s.allCodesByCredentialId);
        return presentToken()
                .thenCompose(token -> protocol.createVpToken(token, s.authRequest, toPresent, passcode))
                .handle((body, t) -> {
                    if (t != null) {
                        session = null;
                        throw new CompletionException(t);
                    }
                    s.responseBody = body;
                    return null;
                });
    }

    public CompletableFuture<Void> submitOid4vpPresentation() {
        SdkSession s = session;
        if (s == null || s.responseBody == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("signOid4vpWithPin() not called"));
        }
        return protocol.submit(s.authRequest, s.responseBody).whenComplete((v, t) -> session = null);
    }

    public void cancelOid4vpPresentation() {
        session = null;
    }

    private static List<MatchedCredential> applyDisclosure(List<MatchedCredential> matched,
                                                           Set<String> includedCodes,
                                                           Map<String, List<String>> allCodesByCredentialId) {
        if (includedCodes == null || matched == null) {
            return matched;
        }
        List<MatchedCredential> out = new ArrayList<>(matched.size());
        for (MatchedCredential mc : matched) {
            List<String> allCodes = allCodesByCredentialId.get(mc.getCredentialId());
            List<String> kept = new ArrayList<>();
            if (allCodes != null) {
                for (String c : allCodes) {
                    if (includedCodes.contains(c) && !kept.contains(c)) {
                        kept.add(c);
                    }
                }
            }

            if (kept.isEmpty()) {
                continue;
            }
            out.add(new MatchedCredential(mc.getQueryId(), mc.getCredentialId(), kept));
        }
        return out;
    }

    private VpScenario buildScenario(SdkSession s, String token) {
        AuthorizationRequest ar = s.authRequest;
        String clientId = safe(ar.getClientId());
        String verifierName = ar.getClientMetadata() != null
                ? safe(ar.getClientMetadata().getClientName())
                : "";
        if (verifierName.isEmpty()) {
            verifierName = lastSegment(clientId);
        }
        VpVerifier verifier = new VpVerifier(clientId, verifierName);

        Map<String, String> formatByQueryId = formatByQueryId(ar);
        List<SdJwtCredentialItem> sdItems = null;
        List<VerifiableCredential> opendidVcs = null;

        List<VpRequestedDoc> docs = new ArrayList<>();
        List<MatchedCredential> matched = s.matched != null ? s.matched : Collections.emptyList();

        boolean mustPin = false;
        boolean mustBio = false;
        Boolean bioEnrolled = null;
        for (MatchedCredential mc : matched) {
            String format = formatByQueryId.get(mc.getQueryId());
            if (CredentialFormat.SD_JWT_VC.matches(format)) {
                if (sdItems == null) {
                    sdItems = loadOid4vcItems(token);
                }
                SdJwtCredentialItem item = findSdItem(sdItems, mc.getCredentialId());
                if (item != null && Constants.KEY_ID_BIO.equals(item.getKid())) {
                    mustBio = true;
                } else {
                    mustPin = true;
                }
                docs.add(buildSdJwtDoc(s, mc, item, toCodeSet(mc.getClaimCodes())));
            } else {
                if (opendidVcs == null) {
                    opendidVcs = loadOpendidVcs(token);
                }
                if (bioEnrolled == null) {
                    bioEnrolled = isBioEnrolled();
                }
                if (!bioEnrolled) {
                    mustPin = true;
                }
                docs.add(buildOpendidVcDoc(s, mc, findOpendidVc(opendidVcs, mc.getCredentialId()),
                        toCodeSet(mc.getClaimCodes())));
            }
        }
        if (docs.isEmpty()) {
            docs.add(new VpRequestedDoc("", "Requested credentials", Collections.emptyList()));
        }

        VerifyAuthType.VERIFY_AUTH_TYPE authType;
        if (mustPin) {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.PIN;
        } else if (mustBio) {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.BIO;
        } else if (!matched.isEmpty()) {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.PIN_OR_BIO;
        } else {
            authType = VerifyAuthType.VERIFY_AUTH_TYPE.PIN;
        }

        String scenarioId = "oid4vp:" + safe(ar.getState());
        return VpScenario.oid4vp(scenarioId, verifier, docs, authType);
    }

    private VpRequestedDoc buildOpendidVcDoc(SdkSession s, MatchedCredential mc,
                                       VerifiableCredential vc,
                                       Set<String> requestedCodes) {
        String docId = mc.getCredentialId();
        String schemaId = (vc != null && vc.getCredentialSchema() != null)
                ? safe(vc.getCredentialSchema().getId()) : "";
        String docTitle = VcSchemaTitle.resolve(schemaId);

        List<String> allCodes = new ArrayList<>();
        List<VpDocEntry> entries = new ArrayList<>();
        if (vc != null && vc.getCredentialSubject() != null
                && vc.getCredentialSubject().getClaims() != null) {
            for (Claim c : vc.getCredentialSubject().getClaims()) {
                String label = safe(c.getCaption());
                if (label.isEmpty()) {
                    label = safe(c.getCode());
                }
                String value = c.getValue() != null ? c.getValue() : "";
                boolean locked = requestedCodes.contains(c.getCode());
                entries.add(VpDocEntry.of(
                        new VpRequestedClaim(label, value, c.getCode(), locked)));

                s.claimKeyToCode.put(
                        docId + ":" + c.getCode(), Collections.singletonList(c.getCode()));
                allCodes.add(c.getCode());
            }
        }
        s.allCodesByCredentialId.put(mc.getCredentialId(), allCodes);
        return new VpRequestedDoc(docId, docTitle, entries);
    }

    private VpRequestedDoc buildSdJwtDoc(SdkSession s, MatchedCredential mc,
                                         SdJwtCredentialItem item,
                                         Set<String> requestedCodes) {
        String docId = mc.getCredentialId();
        String compact = (item != null && item.getSdjwt() != null) ? item.getSdjwt().getCompact() : null;
        String docTitle = (item != null) ? safe(item.getConfigurationId()) : "";
        if (docTitle.isEmpty()) {
            docTitle = "Requested credentials";
        }

        List<String> allCodes = new ArrayList<>();
        List<VpDocEntry> entries = new ArrayList<>();
        if (compact != null) {
            try {
                SdJwt parsed = SdJwtParser.parse(compact);
                for (Map.Entry<String, Object> e : parsed.getDisclosedClaims().entrySet()) {
                    Object value = e.getValue();
                    if (value instanceof Map) {

                        List<VpRequestedClaim> subItems = new ArrayList<>();
                        for (Map.Entry<?, ?> sub : ((Map<?, ?>) value).entrySet()) {
                            Object subVal = sub.getValue();
                            String subKey = String.valueOf(sub.getKey());
                            subItems.add(new VpRequestedClaim(
                                    subKey,
                                    subVal == null ? "" : String.valueOf(subVal),
                                    subKey,  false));
                        }
                        if (!subItems.isEmpty()) {
                            String groupTitle = e.getKey();

                            List<String> groupNames = new ArrayList<>();
                            groupNames.add(e.getKey());
                            for (VpRequestedClaim sub : subItems) {
                                groupNames.add(sub.code);
                            }
                            boolean groupLocked = false;
                            for (String name : groupNames) {
                                if (requestedCodes.contains(name)) {
                                    groupLocked = true;
                                    break;
                                }
                            }
                            entries.add(VpDocEntry.of(
                                    new VpRequestedClaimGroup(groupTitle, subItems, groupLocked)));

                            String repKey = docId + ":" + groupTitle + ":" + subItems.get(0).code;
                            s.claimKeyToCode.put(repKey, groupNames);
                            allCodes.addAll(groupNames);
                        }
                    } else if (value instanceof List) {
                        String arrayCode = e.getKey();
                        boolean locked = requestedCodes.contains(arrayCode);
                        List<VpRequestedClaim> subItems = new ArrayList<>();
                        for (Object el : (List<?>) value) {
                            if (el instanceof Map) {
                                for (Map.Entry<?, ?> f : ((Map<?, ?>) el).entrySet()) {
                                    Object fv = f.getValue();
                                    subItems.add(new VpRequestedClaim(
                                            String.valueOf(f.getKey()),
                                            fv == null ? "" : String.valueOf(fv),
                                            arrayCode, locked));
                                }
                            } else {
                                subItems.add(new VpRequestedClaim(
                                        "", el == null ? "" : String.valueOf(el),
                                        arrayCode, locked));
                            }
                        }
                        if (!subItems.isEmpty()) {
                            entries.add(VpDocEntry.of(
                                    new VpRequestedClaimGroup(arrayCode, subItems, locked)));
                            s.claimKeyToCode.put(
                                    docId + ":" + arrayCode + ":" + arrayCode,
                                    Collections.singletonList(arrayCode));
                        } else {
                            entries.add(VpDocEntry.of(new VpRequestedClaim(
                                    arrayCode, String.valueOf(value), arrayCode, locked)));
                            s.claimKeyToCode.put(
                                    docId + ":" + arrayCode, Collections.singletonList(arrayCode));
                        }
                        allCodes.add(arrayCode);
                    } else {
                        String str = value != null ? String.valueOf(value) : "";
                        boolean locked = requestedCodes.contains(e.getKey());
                        entries.add(VpDocEntry.of(new VpRequestedClaim(
                                e.getKey(), str, e.getKey(),  locked)));
                        s.claimKeyToCode.put(
                                docId + ":" + e.getKey(), Collections.singletonList(e.getKey()));
                        allCodes.add(e.getKey());
                    }
                }
            } catch (RuntimeException | WalletCoreException ignored) {

            }
        }
        s.allCodesByCredentialId.put(mc.getCredentialId(), allCodes);
        return new VpRequestedDoc(docId, docTitle, entries);
    }

    private CompletableFuture<String> presentToken() {
        return GetWalletTokenOp.call(walletGateway, net, config, appContext, identity.getUserId(),
                WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP);
    }

    @NonNull
    private List<SdJwtCredentialItem> loadOid4vcItems(String token) {
        try {
            return walletGateway.getAllOID4VCs(token).get();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private List<VerifiableCredential> loadOpendidVcs(String token) {
        try {
            List<VerifiableCredential> vcs = walletApi.getAllCredentials(token);
            return vcs != null ? vcs : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean isBioEnrolled() {
        try {
            Boolean b = walletGateway.isSavedKey(Constants.KEY_ID_BIO).get();
            return Boolean.TRUE.equals(b);
        } catch (Exception e) {
            return false;
        }
    }

    private static SdJwtCredentialItem findSdItem(List<SdJwtCredentialItem> items, String credentialId) {
        for (SdJwtCredentialItem it : items) {
            if (it.getId() != null && it.getId().equals(credentialId)) {
                return it;
            }
        }
        return null;
    }

    private static VerifiableCredential findOpendidVc(List<VerifiableCredential> vcs, String credentialId) {
        for (VerifiableCredential vc : vcs) {
            if (vc.getId() != null && vc.getId().equals(credentialId)) {
                return vc;
            }
        }
        return null;
    }

    @NonNull
    private static Set<String> toCodeSet(@Nullable List<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isEmpty()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    @NonNull
    private static Map<String, String> formatByQueryId(AuthorizationRequest ar) {
        Map<String, String> map = new LinkedHashMap<>();
        DCQLQuery dcql = ar.getDcqlQuery();
        if (dcql != null && dcql.getCredentials() != null) {
            for (CredentialQuery cq : dcql.getCredentials()) {
                if (cq.getId() != null) {
                    map.put(cq.getId(), cq.getFormat());
                }
            }
        }
        return map;
    }

    private static String lastSegment(String s) {
        if (s == null || s.isEmpty()) return "";
        int slash = s.lastIndexOf('/');
        int colon = s.lastIndexOf(':');
        int idx = Math.max(slash, colon);
        return idx >= 0 && idx < s.length() - 1 ? s.substring(idx + 1) : s;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
