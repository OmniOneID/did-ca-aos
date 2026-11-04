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
import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.model.VpClaimKeys;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedClaimGroup;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpVerifier;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.data.statuslist.CredentialStatusRefs;
import org.omnione.did.ca.protocol.OID4VPProtocol;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.VcSchemaTitle;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.core.oid4vc.format.sdjwt.SdJwtParser;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.oid4vc.AuthorizationRequest;
import org.omnione.did.sdk.datamodel.oid4vc.CredentialFormat;
import org.omnione.did.sdk.datamodel.oid4vc.MatchedCredential;
import org.omnione.did.sdk.datamodel.oid4vc.MdocCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwt;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwtCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.dcql.CredentialQuery;
import org.omnione.did.sdk.datamodel.oid4vc.dcql.DCQLQuery;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;
import org.omnione.did.sdk.utility.Errors.UtilityException;
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
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

@Singleton
public final class Oid4vpSubmissionRepository {

    private static final String TAG = "Oid4vpSubmissionRepo";

    private final OID4VPProtocol protocol;
    private final WalletGateway walletGateway;
    private final WalletApi walletApi;
    private final NetworkManager net;
    private final AppConfig config;
    private final UserIdentityRepository identity;
    private final CredentialStatusRepository statusRepo;
    private final Context appContext;
    private volatile SdkSession session;

    private static final class SdkSession {
        AuthorizationRequest authRequest;
        List<MatchedCredential> matched;
        byte[] responseBody;

        final LinkedHashMap<String, List<String>> claimKeyToCode = new LinkedHashMap<>();
        final Map<String, List<String>> allCodesByOptionId = new LinkedHashMap<>();
        final Map<String, MatchedCredential> byOptionId = new LinkedHashMap<>();
    }

    private static String optionId(MatchedCredential mc) {
        return safe(mc.getQueryId()) + "#" + safe(mc.getCredentialId());
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
                                      OID4VPProtocol protocol,
                                      CredentialStatusRepository statusRepo) {
        this.walletGateway = walletGateway;
        this.walletApi = walletApi;
        this.net = net;
        this.config = config;
        this.identity = identity;
        this.appContext = appContext;
        this.protocol = protocol;
        this.statusRepo = statusRepo;
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
                                                     @NonNull String selectedOptionId,
                                                     @NonNull Set<String> includedClaimKeys) {
        return doSign(pin, selectedOptionId, includedClaimKeys);
    }

    public CompletableFuture<Void> signOid4vpWithBio(@NonNull String selectedOptionId,
                                                     @NonNull Set<String> includedClaimKeys) {
        return doSign(null, selectedOptionId, includedClaimKeys);
    }

    private CompletableFuture<Void> doSign(@Nullable String passcode,
                                           @NonNull String selectedOptionId,
                                           @NonNull Set<String> includedClaimKeys) {
        SdkSession s = session;
        if (s == null) {
            return CaUtil.failedFuture(
                    new IllegalStateException("prepareOid4vpPresentation() not called"));
        }

        Set<String> includedCodes = new HashSet<>();
        for (Map.Entry<String, List<String>> e : s.claimKeyToCode.entrySet()) {
            if (includedClaimKeys.contains(e.getKey())) {
                includedCodes.addAll(e.getValue());
            }
        }
        final List<MatchedCredential> toPresent;
        try {
            toPresent = selectOne(s, selectedOptionId, includedCodes);
        } catch (IllegalStateException e) {
            return CaUtil.failedFuture(e);
        }
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
            return CaUtil.failedFuture(
                    new IllegalStateException("signOid4vpWithPin() not called"));
        }
        return protocol.submit(s.authRequest, s.responseBody).whenComplete((v, t) -> session = null);
    }

    public void cancelOid4vpPresentation() {
        session = null;
    }

    private static List<MatchedCredential> selectOne(SdkSession s, String optionId,
                                                     Set<String> includedCodes) {
        MatchedCredential mc = s.byOptionId.get(optionId);
        if (mc == null) {
            throw new IllegalStateException("unknown option: " + optionId);
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
            throw new IllegalStateException("nothing to disclose for option: " + optionId);
        }
        return Collections.singletonList(
                new MatchedCredential(mc.getQueryId(), mc.getCredentialId(), kept));
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
        if (formatByQueryId.size() > 1) {

            AppLog.w(TAG, "DCQL declares " + formatByQueryId.size() + " credential queries;"
                    + " candidates are flattened into one option list and only the selected"
                    + " credential is submitted, so the verifier may see a partial response");
        }

        List<SdJwtCredentialItem> sdItems = loadOid4vcItems(token);
        List<MdocCredentialItem> mdocItems = loadMdocItems(token);
        List<VerifiableCredential> opendidVcs = loadOpendidVcs(token);

        List<MatchedCredential> matched = filterActive(
                s.matched != null ? s.matched : Collections.<MatchedCredential>emptyList(),
                formatByQueryId, sdItems, mdocItems, opendidVcs);
        if (matched.isEmpty()) {
            throw new NoActiveCredentialException("No ACTIVE credential matches the request");
        }
        s.matched = matched;

        List<VpRequestedDoc> docs = new ArrayList<>();
        boolean mustPin = false;
        boolean mustBio = false;
        Boolean bioEnrolled = null;
        for (MatchedCredential mc : matched) {
            s.byOptionId.put(optionId(mc), mc);
            String format = formatByQueryId.get(mc.getQueryId());
            if (CredentialFormat.SD_JWT_VC.matches(format)) {
                SdJwtCredentialItem item = findSdItem(sdItems, mc.getCredentialId());
                if (item != null && Constants.KEY_ID_BIO.equals(item.getKid())) {
                    mustBio = true;
                } else {
                    mustPin = true;
                }
                docs.add(buildSdJwtDoc(s, mc, item, toCodeSet(mc.getClaimCodes())));
            } else if (CredentialFormat.MSO_MDOC.matches(format)) {
                MdocCredentialItem item = findMdocItem(mdocItems, mc.getCredentialId());
                if (item != null && Constants.KEY_ID_BIO.equals(item.getKid())) {
                    mustBio = true;
                } else {
                    mustPin = true;
                }
                docs.add(buildMdocDoc(s, mc, item, toCodeSet(mc.getClaimCodes())));
            } else {
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

    private List<MatchedCredential> filterActive(List<MatchedCredential> matched,
                                                 Map<String, String> formatByQueryId,
                                                 List<SdJwtCredentialItem> sdItems,
                                                 List<MdocCredentialItem> mdocItems,
                                                 List<VerifiableCredential> opendidVcs) {
        if (matched.isEmpty()) return matched;

        Map<String, CredentialStatusRef> refByCredentialId = new LinkedHashMap<>();
        for (MatchedCredential mc : matched) {
            String credentialId = mc.getCredentialId();
            if (credentialId == null || refByCredentialId.containsKey(credentialId)) continue;
            String format = formatByQueryId.get(mc.getQueryId());
            if (CredentialFormat.SD_JWT_VC.matches(format)) {
                SdJwtCredentialItem item = findSdItem(sdItems, credentialId);
                if (item != null) {
                    refByCredentialId.put(credentialId, CredentialStatusRefs.fromSdJwtItem(item));
                }
            } else if (CredentialFormat.MSO_MDOC.matches(format)) {
                MdocCredentialItem item = findMdocItem(mdocItems, credentialId);
                if (item != null) {
                    refByCredentialId.put(credentialId, CredentialStatusRefs.fromMdocItem(item));
                }
            } else {
                VerifiableCredential vc = findOpendidVc(opendidVcs, credentialId);
                if (vc != null) {
                    refByCredentialId.put(credentialId, CredentialStatusRefs.fromOpenDidVc(vc));
                }
            }
        }

        Map<String, VcStatus> statuses;
        try {
            statuses = statusRepo.refreshSubset(new ArrayList<>(refByCredentialId.values())).get();
        } catch (ExecutionException | InterruptedException | RuntimeException e) {
            AppLog.w(TAG, "status refresh failed, keeping every candidate: " + e);
            return matched;
        }

        List<MatchedCredential> active = new ArrayList<>(matched.size());
        for (MatchedCredential mc : matched) {
            VcStatus status = statuses.get(mc.getCredentialId());
            if (status == null || status == VcStatus.ACTIVE) {
                active.add(mc);
            }
        }
        return active;
    }

    private VpRequestedDoc buildOpendidVcDoc(SdkSession s, MatchedCredential mc,
                                       VerifiableCredential vc,
                                       Set<String> requestedCodes) {
        String docId = optionId(mc);
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
                        VpClaimKeys.flat(docId, c.getCode()),
                        Collections.singletonList(c.getCode()));
                allCodes.add(c.getCode());
            }
        }
        s.allCodesByOptionId.put(docId, allCodes);
        return new VpRequestedDoc(docId, docTitle, entries);
    }

    private VpRequestedDoc buildSdJwtDoc(SdkSession s, MatchedCredential mc,
                                         SdJwtCredentialItem item,
                                         Set<String> requestedCodes) {
        String docId = optionId(mc);
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
                            String subKey = String.valueOf(sub.getKey());
                            subItems.add(VpClaimFactory.leaf(subKey, sub.getValue(), subKey, false));
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

                            String repKey = VpClaimKeys.groupChild(
                                    docId, groupTitle, subItems.get(0).code);
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
                                    subItems.add(VpClaimFactory.leaf(
                                            String.valueOf(f.getKey()), f.getValue(), arrayCode, locked));
                                }
                            } else {
                                subItems.add(VpClaimFactory.leaf("", el, arrayCode, locked));
                            }
                        }
                        if (!subItems.isEmpty()) {
                            entries.add(VpDocEntry.of(
                                    new VpRequestedClaimGroup(arrayCode, subItems, locked)));
                            s.claimKeyToCode.put(
                                    VpClaimKeys.groupChild(docId, arrayCode, arrayCode),
                                    Collections.singletonList(arrayCode));
                        } else {
                            entries.add(VpDocEntry.of(new VpRequestedClaim(
                                    arrayCode, String.valueOf(value), arrayCode, locked)));
                            s.claimKeyToCode.put(
                                    VpClaimKeys.flat(docId, arrayCode),
                                    Collections.singletonList(arrayCode));
                        }
                        allCodes.add(arrayCode);
                    } else {
                        boolean locked = requestedCodes.contains(e.getKey());
                        entries.add(VpDocEntry.of(
                                VpClaimFactory.leaf(e.getKey(), value, e.getKey(), locked)));
                        s.claimKeyToCode.put(
                                VpClaimKeys.flat(docId, e.getKey()),
                                Collections.singletonList(e.getKey()));
                        allCodes.add(e.getKey());
                    }
                }
            } catch (RuntimeException | WalletCoreException ignored) {
                AppLog.d(TAG, "sd-jwt claim mapping skipped: " + ignored);
            }
        }
        s.allCodesByOptionId.put(docId, allCodes);
        return new VpRequestedDoc(docId, docTitle, entries);
    }

    private VpRequestedDoc buildMdocDoc(SdkSession s, MatchedCredential mc,
                                        MdocCredentialItem item,
                                        Set<String> requestedCodes) {
        String docId = optionId(mc);
        MdocVpDocBuilder.Result result = MdocVpDocBuilder.build(docId, item, requestedCodes);
        s.claimKeyToCode.putAll(result.claimKeyToCode);
        s.allCodesByOptionId.put(docId, result.allCodes);
        return result.doc;
    }

    private CompletableFuture<String> presentToken() {
        return GetWalletTokenOp.call(walletGateway, net, config, appContext, identity.getUserId(),
                WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP);
    }

    @NonNull
    private List<SdJwtCredentialItem> loadOid4vcItems(String token) {
        try {
            return walletGateway.getAllSdJwtOID4VCs(token).get();
        } catch (ExecutionException | InterruptedException | RuntimeException e) {
            return Collections.emptyList();
        }
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

    @NonNull
    private List<VerifiableCredential> loadOpendidVcs(String token) {
        try {
            List<VerifiableCredential> vcs = walletApi.getAllCredentials(token);
            return vcs != null ? vcs : Collections.emptyList();
        } catch (UtilityException | WalletCoreException | WalletException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private boolean isBioEnrolled() {
        try {
            Boolean b = walletGateway.isSavedKey(Constants.KEY_ID_BIO).get();
            return Boolean.TRUE.equals(b);
        } catch (ExecutionException | InterruptedException | RuntimeException e) {
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

    private static MdocCredentialItem findMdocItem(List<MdocCredentialItem> items, String credentialId) {
        for (MdocCredentialItem it : items) {
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
