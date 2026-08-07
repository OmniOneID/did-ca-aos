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
import org.omnione.did.ca.data.model.ReferentOption;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.ca.data.model.VpPlainAttribute;
import org.omnione.did.ca.data.model.VpRevealAttribute;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpVerifier;
import org.omnione.did.ca.data.model.VpZkpRequest;
import org.omnione.did.ca.data.model.ZkpAttributeReferent;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.network.protocol.RequestProofProfileOp;
import org.omnione.did.ca.data.network.protocol.RequestVerifyProofOp;
import org.omnione.did.ca.data.network.protocol.ZkpCredDefOp;
import org.omnione.did.ca.data.network.protocol.ZkpCredSchemaOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.profile.ProofRequestProfile;
import org.omnione.did.sdk.datamodel.profile.ProviderDetail;
import org.omnione.did.sdk.datamodel.protocol.P311RequestVo;
import org.omnione.did.sdk.datamodel.zkp.AttrReferent;
import org.omnione.did.sdk.datamodel.zkp.AttributeDef;
import org.omnione.did.sdk.datamodel.zkp.AttributeInfo;
import org.omnione.did.sdk.datamodel.zkp.AttributeType;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.Namespace;
import org.omnione.did.sdk.datamodel.zkp.PredicateInfo;
import org.omnione.did.sdk.datamodel.zkp.PredicateReferent;
import org.omnione.did.sdk.datamodel.zkp.ProofParam;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.zkp.Referent;
import org.omnione.did.sdk.datamodel.zkp.ReferentInfo;
import org.omnione.did.sdk.datamodel.zkp.SubReferent;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
public final class ZkpSubmissionRepository {

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
        ProofRequestProfile profile;
        AvailableReferent availableReferent;
        String requestVoJson;

        final Map<String, CredentialDefinition> credDefById = new HashMap<>();

        final Map<String, CredentialSchema> schemaById = new HashMap<>();

        final Map<String, String> captionByKey = new HashMap<>();

        SdkSession(String offerId) {
            this.offerId = offerId;
        }
    }

    @Inject
    public ZkpSubmissionRepository(@ApplicationContext Context appContext,
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

        public CompletableFuture<VpScenario> prepareZkpPresentation(@NonNull String offerId,
                                                                @NonNull String txId) {
        SdkSession s = new SdkSession(offerId);
        s.txId = txId;
        session = s;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String verifier = config.verifierUrl();

                RequestProofProfileOp.Result pr = RequestProofProfileOp.call(
                        net, appContext, verifier, offerId, s.txId);
                s.txId = pr.txId;
                s.profile = pr.profile;

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP).get();

                ProofRequest proofRequest = s.profile.getProfile().getProofRequest();

                normalizeRestrictionKeys(proofRequest);
                s.availableReferent =
                        walletGateway.searchZkpCredentials(s.hWalletToken, proofRequest).get();

                Set<String> credDefIds = collectCredDefIds(proofRequest);
                String apiGw = config.apiGwUrl();
                for (String credDefId : credDefIds) {
                    if (credDefId == null || credDefId.isEmpty()) continue;
                    CredentialDefinition def = ZkpCredDefOp.call(net, apiGw, credDefId);
                    s.credDefById.put(credDefId, def);
                    String schemaId = def != null ? def.getSchemaId() : null;
                    if (schemaId != null && !schemaId.isEmpty()
                            && !s.schemaById.containsKey(schemaId)) {
                        s.schemaById.put(schemaId, ZkpCredSchemaOp.call(net, apiGw, schemaId));
                    }
                }
                populateCaptionMap(s.captionByKey, s.schemaById.values());

                return buildScenario(offerId, s.profile, s.availableReferent,
                        s.captionByKey, s.credDefById, s.schemaById);
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private VpScenario buildScenario(String offerId,
                                     ProofRequestProfile profile,
                                     AvailableReferent available,
                                     Map<String, String> captionByKey,
                                     Map<String, CredentialDefinition> credDefById,
                                     Map<String, CredentialSchema> schemaById) {
        ProviderDetail v = profile.getProfile().getVerifier();
        VpVerifier verifier = new VpVerifier(safe(v.getDID()), safe(v.getName()));

        ProofRequest proofRequest = profile.getProfile().getProofRequest();
        Map<String, AttributeInfo> reqAttrInfo = proofRequest != null
                ? proofRequest.getRequestedAttributes()
                : null;
        Map<String, PredicateInfo> reqPredInfo = proofRequest != null
                ? proofRequest.getRequestedPredicates()
                : null;

        Map<String, ZkpAttributeReferent> referentsByLabel = new HashMap<>();
        List<VpRevealAttribute> attributes = new ArrayList<>();
        Map<String, AttrReferent> attrMap = available.getAttrReferent();
        if (attrMap != null) {
            for (String referentKey : orderedKeys(reqAttrInfo, attrMap)) {
                AttrReferent ar = attrMap.get(referentKey);
                String rawName = safe(ar.getName());
                String label = displayLabel(rawName, captionByKey);
                List<SubReferent> subs = ar.getAttrSubReferent();

                if (subs == null || subs.isEmpty()) continue;

                String schemaName = lookupSchemaName(referentKey, reqAttrInfo,
                        credDefById, schemaById);

                List<ReferentOption> options = new ArrayList<>();
                for (SubReferent sub : subs) {
                    options.add(new ReferentOption(
                            schemaName, safe(sub.getRaw()), safe(sub.getCredentialId())));
                }
                referentsByLabel.put(label,
                        new ZkpAttributeReferent(referentKey, label, options));
                attributes.add(new VpRevealAttribute(label, null,  true));
            }
        }

        List<VpPlainAttribute> predicates = new ArrayList<>();
        Map<String, PredicateReferent> predMap = available.getPredicateReferent();
        if (predMap != null) {
            for (String referentKey : orderedKeys(reqPredInfo, predMap)) {
                PredicateReferent pr = predMap.get(referentKey);
                List<SubReferent> subs = pr.getPredicateSubReferent();
                String value = (subs != null && !subs.isEmpty()) ? safe(subs.get(0).getRaw()) : "";
                predicates.add(new VpPlainAttribute(
                        displayLabel(safe(pr.getName()), captionByKey), value));
            }
        }

        List<VpPlainAttribute> selfAttrs = new ArrayList<>();
        Map<String, AttrReferent> selfMap = available.getSelfAttrReferent();
        if (selfMap != null) {
            for (String referentKey : orderedKeys(reqAttrInfo, selfMap)) {
                selfAttrs.add(new VpPlainAttribute(
                        displayLabel(safe(selfMap.get(referentKey).getName()), captionByKey), ""));
            }
        }

        VpZkpRequest zkpReq = new VpZkpRequest(
                attributes, predicates, selfAttrs, referentsByLabel);

        return VpScenario.zkp(offerId, verifier, zkpReq, VerifyAuthType.VERIFY_AUTH_TYPE.PIN);
    }

        public CompletableFuture<Void> signZkpWithPin(@NonNull String pin,
                                                  @NonNull Map<String, String> referents,
                                                  @NonNull Map<String, String> credentialIds,
                                                  @NonNull Set<String> revealed) {

        return doSign(referents, credentialIds, revealed);
    }

        public CompletableFuture<Void> signZkpWithBio(@NonNull Map<String, String> referents,
                                                  @NonNull Map<String, String> credentialIds,
                                                  @NonNull Set<String> revealed) {
        return doSign(referents, credentialIds, revealed);
    }

    private CompletableFuture<Void> doSign(@NonNull Map<String, String> selectedReferents,
                                           @NonNull Map<String, String> selectedCredentialIds,
                                           @NonNull Set<String> revealedLabels) {
        SdkSession s = session;
        if (s == null) return failedSession();
        return CompletableFuture.supplyAsync(() -> {
            try {

                List<UserReferent> userReferents = buildUserReferents(
                        s.availableReferent, selectedReferents, selectedCredentialIds,
                        revealedLabels, s.captionByKey);
                if (userReferents.isEmpty()) {
                    throw new IllegalStateException("No matching credential for ZKP request");
                }

                ReferentInfo referentInfo = walletGateway.createZkpReferent(userReferents).get();

                List<ProofParam> proofParams = new LinkedList<>();
                String apiGw = config.apiGwUrl();
                for (Map.Entry<String, Referent> entry : referentInfo.getReferents().entrySet()) {
                    Referent referent = entry.getValue();
                    String credDefId = referent.getCredDefId();
                    String schemaId = referent.getSchemaId();
                    if (!s.credDefById.containsKey(credDefId)) {
                        s.credDefById.put(credDefId, ZkpCredDefOp.call(net, apiGw, credDefId));
                    }
                    if (!s.schemaById.containsKey(schemaId)) {
                        s.schemaById.put(schemaId, ZkpCredSchemaOp.call(net, apiGw, schemaId));
                    }
                    proofParams.add(new ProofParam.Builder()
                            .setCredDef(s.credDefById.get(credDefId))
                            .setSchema(s.schemaById.get(schemaId))
                            .setReferentInfo(new ReferentInfo(entry.getKey(), referent))
                            .build());
                }

                Map<String, String> selfAttr = new HashMap<>();
                P311RequestVo req = walletGateway.createEncZkpProof(
                        s.hWalletToken, proofParams, selfAttr, s.profile, s.txId, apiGw).get();
                s.requestVoJson = req.toJson();
                return null;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

    private static List<String> orderedKeys(Map<String, ?> requested, Map<String, ?> available) {
        List<String> keys = new ArrayList<>();
        if (available == null) return keys;
        if (requested != null) {
            for (String key : requested.keySet()) {
                if (available.containsKey(key)) keys.add(key);
            }
        }
        for (String key : available.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    private static List<UserReferent> buildUserReferents(AvailableReferent available,
                                                         Map<String, String> selected,
                                                         Map<String, String> selectedCredentialIds,
                                                         Set<String> revealed,
                                                         Map<String, String> captionByKey) {
        List<UserReferent> result = new ArrayList<>();

        Map<String, AttrReferent> attrMap = available.getAttrReferent();
        if (attrMap != null) {
            for (Map.Entry<String, AttrReferent> e : attrMap.entrySet()) {
                String referentKey = e.getKey();
                AttrReferent ar = e.getValue();
                String name = ar.getName();
                String displayLabel = displayLabel(safe(name), captionByKey);
                List<SubReferent> subs = ar.getAttrSubReferent();
                if (subs == null || subs.isEmpty()) continue;

                SubReferent chosen = null;
                if (subs.size() == 1) {
                    chosen = subs.get(0);
                } else {
                    String selectedCredId = selectedCredentialIds.get(displayLabel);
                    if (selectedCredId == null) selectedCredId = selectedCredentialIds.get(name);
                    if (selectedCredId != null) {
                        for (SubReferent sr : subs) {
                            if (selectedCredId.equals(sr.getCredentialId())) {
                                chosen = sr;
                                break;
                            }
                        }
                    }
                    if (chosen == null) {
                        String selectedRaw = selected.get(displayLabel);
                        if (selectedRaw == null) selectedRaw = selected.get(name);
                        if (selectedRaw != null) {
                            for (SubReferent sr : subs) {
                                if (selectedRaw.equals(sr.getRaw())) {
                                    chosen = sr;
                                    break;
                                }
                            }
                        }
                    }
                    if (chosen == null) chosen = subs.get(0);
                }

                boolean isRevealed = revealed.contains(displayLabel) || revealed.contains(name);
                result.add(new UserReferent.Builder()
                        .setReferentKey(referentKey)
                        .setReferentName(name)
                        .setRaw(chosen.getRaw())
                        .setCredentialId(chosen.getCredentialId())
                        .setRevealed(isRevealed)
                        .build());
            }
        }

        Map<String, PredicateReferent> predMap = available.getPredicateReferent();
        if (predMap != null) {
            for (Map.Entry<String, PredicateReferent> e : predMap.entrySet()) {
                PredicateReferent pr = e.getValue();
                List<SubReferent> subs = pr.getPredicateSubReferent();
                if (subs == null || subs.isEmpty()) continue;
                SubReferent chosen = subs.get(0);
                result.add(new UserReferent.Builder()
                        .setReferentKey(e.getKey())
                        .setReferentName(pr.getName())
                        .setRaw(chosen.getRaw())
                        .setCredentialId(chosen.getCredentialId())
                        .setRevealed(false)
                        .build());
            }
        }

        return result;
    }

        public CompletableFuture<Void> submitZkpPresentation() {
        SdkSession s = session;
        if (s == null || s.requestVoJson == null) return failedSession();
        return CompletableFuture.runAsync(() -> {
            try {
                RequestVerifyProofOp.call(net, config.verifierUrl(), s.requestVoJson);
                session = null;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

        public void cancelZkpPresentation() {
        session = null;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static Set<String> collectCredDefIds(ProofRequest proofRequest) {
        Set<String> ids = new HashSet<>();
        if (proofRequest.getRequestedAttributes() != null) {
            for (AttributeInfo info : proofRequest.getRequestedAttributes().values()) {
                if (info != null) addCredDefIds(info.getRestrictions(), ids);
            }
        }
        if (proofRequest.getRequestedPredicates() != null) {
            for (PredicateInfo info : proofRequest.getRequestedPredicates().values()) {
                if (info != null) addCredDefIds(info.getRestrictions(), ids);
            }
        }
        return ids;
    }

    private static void addCredDefIds(List<Map<String, String>> restrictions, Set<String> out) {
        if (restrictions == null) return;
        for (Map<String, String> r : restrictions) {
            String id = r.get("credDefId");
            if (id != null && !id.isEmpty()) out.add(id);
        }
    }

    private static void populateCaptionMap(Map<String, String> out,
                                           Iterable<CredentialSchema> schemas) {
        for (CredentialSchema schema : schemas) {
            if (schema == null || schema.getAttrTypes() == null) continue;
            for (AttributeType type : schema.getAttrTypes()) {
                Namespace ns = type.getNamespace();
                if (ns == null || ns.getId() == null) continue;
                String prefix = ns.getId();
                if (type.getItems() == null) continue;
                for (AttributeDef def : type.getItems()) {
                    if (def == null || def.getLabel() == null) continue;
                    String key = prefix + "." + def.getLabel();
                    String caption = (def.getCaption() != null && !def.getCaption().isEmpty())
                            ? def.getCaption() : def.getLabel();
                    out.putIfAbsent(key, caption);
                }
            }
        }
    }

    private static String displayLabel(String rawKey, Map<String, String> captionByKey) {
        if (rawKey == null || rawKey.isEmpty()) return rawKey;
        String caption = captionByKey != null ? captionByKey.get(rawKey) : null;
        return caption != null ? caption : rawKey;
    }

    private static String lookupSchemaName(String referentKey,
                                           Map<String, AttributeInfo> reqAttrInfo,
                                           Map<String, CredentialDefinition> credDefById,
                                           Map<String, CredentialSchema> schemaById) {
        if (referentKey == null || reqAttrInfo == null) return "";
        AttributeInfo info = reqAttrInfo.get(referentKey);
        if (info == null || info.getRestrictions() == null
                || info.getRestrictions().isEmpty()) return "";
        for (Map<String, String> r : info.getRestrictions()) {
            String credDefId = r.get("credDefId");
            if (credDefId == null || credDefId.isEmpty()) continue;
            CredentialDefinition def = credDefById.get(credDefId);
            if (def == null) continue;
            String schemaId = def.getSchemaId();
            if (schemaId == null) continue;
            CredentialSchema schema = schemaById.get(schemaId);
            if (schema == null) continue;
            String name = schema.getName();
            if (name != null && !name.isEmpty()) return name;
        }
        return "";
    }

    private static void normalizeRestrictionKeys(ProofRequest proofRequest) {
        if (proofRequest.getRequestedAttributes() != null) {
            for (AttributeInfo info : proofRequest.getRequestedAttributes().values()) {
                if (info != null) normalizeRestrictionsList(info.getRestrictions());
            }
        }
        if (proofRequest.getRequestedPredicates() != null) {
            for (PredicateInfo info : proofRequest.getRequestedPredicates().values()) {
                if (info != null) normalizeRestrictionsList(info.getRestrictions());
            }
        }
    }

    private static void normalizeRestrictionsList(List<Map<String, String>> restrictions) {
        if (restrictions == null) return;
        for (Map<String, String> r : restrictions) {
            replaceKey(r, "cred_def_id", "credDefId");
            replaceKey(r, "schema_id", "schemaId");
            replaceKey(r, "issuer_did", "issuerDid");
        }
    }

    private static void replaceKey(Map<String, String> m, String oldKey, String newKey) {
        if (m.containsKey(oldKey)) {
            m.put(newKey, m.remove(oldKey));
        }
    }

    private static <T> CompletableFuture<T> failedSession() {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException("no session"));
        return f;
    }
}
