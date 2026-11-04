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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.model.CredentialClaim;
import org.omnione.did.ca.data.model.VcDetailResult;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.network.protocol.RequestVcSchemaOp;
import org.omnione.did.ca.data.network.protocol.RequestZkpSchemaOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.data.statuslist.CredentialStatusRefs;
import org.omnione.did.ca.data.statuslist.MdocStatusListReader;
import org.omnione.did.ca.data.statuslist.StatusListReference;
import org.omnione.did.ca.data.statuslist.StatusListResult;
import org.omnione.did.ca.protocol.OID4VCIProtocol;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.ClaimImages;
import org.omnione.did.ca.util.CredentialDateFormatter;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.oid4vc.model.IssuerMetadataResponse;
import org.omnione.did.sdk.datamodel.common.enums.ClaimType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.oid4vc.Mdoc;
import org.omnione.did.sdk.datamodel.oid4vc.MdocCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.MdocMso;
import org.omnione.did.sdk.datamodel.oid4vc.MdocValidityInfo;
import org.omnione.did.sdk.datamodel.oid4vc.OID4VCCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwt;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwtCredentialItem;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;
import org.omnione.did.sdk.datamodel.vcschema.VCSchema;
import org.omnione.did.sdk.datamodel.zkp.AttributeDef;
import org.omnione.did.sdk.datamodel.zkp.AttributeType;
import org.omnione.did.sdk.datamodel.zkp.AttributeValue;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchemaVo;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class CredentialRepository {

    private static final String TAG = "DocsVc";

    private final WalletGateway gateway;
    private final NetworkManager net;
    private final AppConfig config;
    private final UserIdentityRepository identity;
    private final Oid4vcIssuerRepository issuers;
    private final OID4VCIProtocol protocol;
    private final CredentialStatusRepository statusRepo;
    private final Context appCtx;
    private final Executor executor = Executors.newCachedThreadPool();

    private volatile Set<String> oid4vcIds = Collections.emptySet();

    private volatile CompletableFuture<WalletLoad> inFlightFetchWallet;

    private final Map<String, String> schemaTitles = new ConcurrentHashMap<>();
    private final Map<String, CredentialSchema> zkpSchemas = new ConcurrentHashMap<>();

    @Inject
    public CredentialRepository(WalletGateway gateway,
                                       NetworkManager net,
                                       AppConfig config,
                                       UserIdentityRepository identity,
                                       Oid4vcIssuerRepository issuers,
                                       OID4VCIProtocol protocol,
                                       CredentialStatusRepository statusRepo,
                                       @ApplicationContext Context appCtx) {
        this.gateway = gateway;
        this.net = net;
        this.config = config;
        this.identity = identity;
        this.issuers = issuers;
        this.protocol = protocol;
        this.statusRepo = statusRepo;
        this.appCtx = appCtx;
    }

    @NonNull
        public synchronized CompletableFuture<WalletLoad> fetchWallet() {
        CompletableFuture<WalletLoad> existing = inFlightFetchWallet;
        if (existing != null && !existing.isDone()) {
            AppLog.d(TAG, "fetchWallet: in-flight, returning existing future");
            return existing;
        }
        CompletableFuture<WalletLoad> f = gateway.createLocalWalletToken(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC, CaUtil.getPackageName(appCtx))
                .thenCompose(token -> gateway.getAllCredentials(token)
                        .thenCompose(this::mapToCredentials)

                        .thenCompose(opendidVc -> gateway.getAllOID4VCs(token)
                                .thenCompose(items -> appendOid4vciCredentials(opendidVc, items))))
                .thenCompose(this::refreshStatuses);
        inFlightFetchWallet = f;
        f.whenComplete((r, e) -> {
            synchronized (CredentialRepository.this) {
                if (inFlightFetchWallet == f) {
                    inFlightFetchWallet = null;
                }
            }
        });
        return f;
    }

    @NonNull
        public CompletableFuture<VcDetailResult> fetchDetail(@NonNull String vcId) {
        return isOid4vc(vcId).thenCompose(oid4vc -> {
            if (oid4vc) {
                return gateway.createLocalWalletToken(
                                WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC, CaUtil.getPackageName(appCtx))
                        .thenCompose(token -> gateway.getOID4VCs(token, List.of(vcId)))
                        .thenApply(this::mapOid4vciDetail);
            }
            return gateway.createLocalWalletToken(
                            WalletTokenPurpose.WALLET_TOKEN_PURPOSE.DETAIL_VC, CaUtil.getPackageName(appCtx))
                    .thenCompose(token -> gateway.getCredential(token, vcId)
                            .thenCompose(vc -> mapDetail(vc, vcId, token)));
        });
    }

    @NonNull
        public CompletableFuture<Void> deleteOid4vc(@NonNull String vcId) {
        return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC)
                .thenCompose(token -> gateway.deleteOID4VCs(token, List.of(vcId)))
                .thenApply(v -> {
                    Set<String> next = new HashSet<>(oid4vcIds);
                    next.remove(vcId);
                    oid4vcIds = next;
                    issuers.remove(vcId);
                    return v;
                });
    }

    @NonNull
    public CompletableFuture<Oid4vcRevokeTarget> prepareOid4vcRevoke(@NonNull String vcId) {
        String issuer = issuers.get(vcId);
        if (issuer == null) {
            AppLog.d(TAG, "revoke-or-local vcId=" + vcId + " explicitRevoked=false issuer=null");
            return CaUtil.failedFuture(
                    new IllegalStateException("no credential_issuer for " + vcId));
        }
        CompletableFuture<String> kidFut = gateway.createLocalWalletToken(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC, CaUtil.getPackageName(appCtx))
                .thenCompose(token -> gateway.getOID4VCs(token, List.of(vcId)))
                .thenApply(items -> {
                    if (items.isEmpty()) {
                        throw new CompletionException(new IllegalStateException("OID4VC not found: " + vcId));
                    }
                    return items.get(0).getKid();
                });
        return protocol.getMetadata(issuer).thenCombine(kidFut, (metadata, kid) -> {
            String endpoint = metadata.getCredentialRevocationEndpoint();
            String nonceEndpoint = metadata.getNonceEndpoint();
            AppLog.d(TAG, "revoke-or-local vcId=" + vcId + " explicitRevoked=false issuer=" + issuer
                    + " endpoint=" + endpoint + " nonce=" + nonceEndpoint + " kid=" + kid);
            if (endpoint == null || endpoint.isEmpty() || nonceEndpoint == null || nonceEndpoint.isEmpty()) {
                throw new CompletionException(
                        new IllegalStateException("no revocation or nonce endpoint for " + issuer));
            }
            return new Oid4vcRevokeTarget(metadata, kid);
        });
    }

    @NonNull
    public CompletableFuture<Void> revokeOid4vcAndDelete(@NonNull String vcId,
                                                         @NonNull IssuerMetadataResponse metadata,
                                                         @Nullable String pin) {
        return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC)
                .thenCompose(token -> gateway.requestRevokeOID4VC(token, metadata, vcId, pin != null ? pin : ""))
                .whenComplete((v, err) -> {
                    if (err == null) {
                        AppLog.d(TAG, "revoke oid4vc ok vcId=" + vcId);
                        return;
                    }
                    AppLog.e(TAG, "revoke oid4vc failed vcId=" + vcId, err);
                    for (Throwable c = err; c != null; c = c.getCause()) {
                        if (c instanceof CommunicationException) {
                            CommunicationException ce = (CommunicationException) c;
                            AppLog.e(TAG, "revoke oid4vc server error code=" + ce.getErrorCode()
                                    + " msg=" + ce.getErrMsg() + " reason=" + ce.getErrorReason());
                            break;
                        }
                    }
                })
                .thenCompose(v -> deleteOid4vc(vcId));
    }

    @NonNull
    private CompletableFuture<Boolean> isOid4vc(@NonNull String vcId) {
        if (oid4vcIds.contains(vcId)) return CompletableFuture.completedFuture(true);
        return gateway.createLocalWalletToken(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC, CaUtil.getPackageName(appCtx))
                .thenCompose(gateway::getAllOID4VCs)
                .thenApply(items -> {
                    Set<String> ids = new HashSet<>();
                    for (OID4VCCredentialItem item : items) ids.add(item.getId());
                    oid4vcIds = ids;
                    return ids.contains(vcId);
                })
                .exceptionally(e -> {
                    AppLog.e(TAG, "isOid4vc: OID4VC lookup failed, treating as OpenDID: vcId=" + vcId, e);
                    return false;
                });
    }

    @NonNull
    private CompletableFuture<List<MappedCredential>> mapToCredentials(@NonNull List<VerifiableCredential> vcList) {
        if (vcList.isEmpty()) return CompletableFuture.completedFuture(Collections.emptyList());

        for (int i = 0; i < vcList.size(); i++) {
            logOpenDidVc(i + 1, vcList.size(), vcList.get(i));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        CompletableFuture<MappedCredential>[] futures = new CompletableFuture[vcList.size()];
        for (int i = 0; i < vcList.size(); i++) {
            futures[i] = mapOne(vcList.get(i));
        }
        return CompletableFuture.allOf(futures).thenApply(v -> {
            List<MappedCredential> result = new ArrayList<>(futures.length);
            for (CompletableFuture<MappedCredential> f : futures) result.add(f.join());
            return result;
        });
    }

    @NonNull
    private CompletableFuture<MappedCredential> mapOne(@NonNull VerifiableCredential vc) {
        String schemaUrl = vc.getCredentialSchema().getId();
        CompletableFuture<String> titleFut = CompletableFuture.supplyAsync(() -> {
            try {
                String body = RequestVcSchemaOp.call(net, schemaUrl);
                VCSchema schema = MessageUtil.deserialize(body, VCSchema.class);
                String title = schema != null ? schema.getTitle() : null;
                if (title != null && !title.isEmpty()) {
                    schemaTitles.put(schemaUrl, title);
                    return title;
                }
            } catch (CommunicationException | RuntimeException e) {
                AppLog.e(TAG, "mapOne: schema fetch failed for " + schemaUrl, e);
            }
            String cached = schemaTitles.get(schemaUrl);
            return cached != null ? cached : nameFromSchemaId(schemaUrl, vc.getId());
        }, executor);
        CompletableFuture<Boolean> zkpFut = gateway.isZkpCredentialsSaved(vc.getId());

        Long expSec = CredentialDateFormatter.toEpochSeconds(vc.getValidUntil());
        CredentialStatusRef statusRef = CredentialStatusRefs.fromOpenDidVc(vc);
        return titleFut.thenCombine(zkpFut, AbstractMap.SimpleImmutableEntry::new)
                .thenApply(entry -> new MappedCredential(
                        new Credential(
                                vc.getId(),
                                entry.getKey(),
                                CredentialBadge.VC,
                                vc.getIssuer() != null ? vc.getIssuer().getName() : "",
                                CredentialDateFormatter.format(vc.getIssuanceDate()),
                                CredentialDateFormatter.format(vc.getValidUntil()),
                                lastKnownStatus(vc.getId(), expSec),
                                entry.getValue()),
                        statusRef));
    }

    @NonNull
    private CompletableFuture<WalletLoad> refreshStatuses(@NonNull List<MappedCredential> mapped) {
        List<CredentialStatusRef> refs = new ArrayList<>(mapped.size());
        for (MappedCredential m : mapped) {
            if (m.statusRef != null) refs.add(m.statusRef);
        }
        return statusRepo.refreshAll(refs).thenApply(statuses -> {
            List<Credential> result = new ArrayList<>(mapped.size());
            boolean checkFailed = false;
            long nowSec = System.currentTimeMillis() / 1000L;
            for (MappedCredential m : mapped) {
                StatusListResult sr = statuses.get(m.credential.id);
                Long expSec = m.statusRef != null ? m.statusRef.expEpochSec : null;
                if (sr != null && sr.checkFailed && (expSec == null || expSec > nowSec)) checkFailed = true;
                result.add(sr == null || sr.status == m.credential.status
                        ? m.credential
                        : m.credential.withStatus(sr.status));
            }
            return new WalletLoad(result, checkFailed);
        });
    }

    @NonNull
    private static String nameFromSchemaId(@Nullable String schemaUrl, @NonNull String fallback) {
        if (schemaUrl == null) return fallback;
        Uri uri = Uri.parse(schemaUrl);
        if (!uri.isHierarchical()) return fallback;
        String name = uri.getQueryParameter("name");
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) return fallback;
        if (name.startsWith("vc.schema.")) name = name.substring("vc.schema.".length());
        StringBuilder sb = new StringBuilder();
        for (String word : name.replace('.', ' ').trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.length() == 0 ? fallback : sb.append("VC").toString();
    }

    @NonNull
    private VcStatus lastKnownStatus(@NonNull String vcId, @Nullable Long expEpochSec) {
        VcStatus known = statusRepo.known(vcId);
        if (known != null) return known;
        if (expEpochSec != null && expEpochSec <= System.currentTimeMillis() / 1000L) {
            return VcStatus.REVOKED;
        }
        return VcStatus.ACTIVE;
    }

    @NonNull
    private CompletableFuture<VcDetailResult> mapDetail(@NonNull VerifiableCredential vc,
                                                        @NonNull String vcId,
                                                        @NonNull String walletToken) {
        CompletableFuture<MappedCredential> credFut = mapOne(vc);
        List<CredentialClaim> claims = mapClaims(vc);

        return credFut.thenCompose(mapped -> {
            Credential c = mapped.credential;
            CredentialStatusRef statusRef = mapped.statusRef;
            if (!c.zkp) {
                return CompletableFuture.completedFuture(
                        new VcDetailResult(c, claims, null, statusRef));
            }
            return gateway.getZkpCredential(walletToken, vcId)
                    .thenCompose(zkpCred -> CompletableFuture.supplyAsync(() -> {
                        String zkpSchemaId = zkpCred.getSchemaId();
                        CredentialSchema schema = null;
                        try {
                            String body = RequestZkpSchemaOp.call(net, config.apiGwUrl(), zkpSchemaId);

                            CredentialSchemaVo vo = MessageUtil.deserialize(body, CredentialSchemaVo.class);
                            String decoded = new String(MultibaseUtils.decode(vo.getCredSchema()));
                            schema = MessageUtil.deserialize(decoded, CredentialSchema.class);
                        } catch (CommunicationException | UtilityException | RuntimeException e) {
                            AppLog.e(TAG, "mapDetail: zkp schema fetch failed for vcId=" + vcId, e);
                        }
                        if (schema != null && zkpSchemaId != null) zkpSchemas.put(zkpSchemaId, schema);
                        if (schema == null && zkpSchemaId != null) schema = zkpSchemas.get(zkpSchemaId);
                        return schema != null ? mapZkpClaims(zkpCred, schema) : fallbackZkpClaims(zkpCred);
                    }, executor))
                    .thenApply(zkpClaims -> new VcDetailResult(c, claims, zkpClaims, statusRef))
                    .exceptionally(e -> {
                        AppLog.e(TAG, "mapDetail: zkp section unavailable for vcId=" + vcId, e);
                        return new VcDetailResult(c, claims, null, statusRef);
                    });
        });
    }

    @NonNull
    private List<CredentialClaim> mapClaims(@NonNull VerifiableCredential vc) {
        List<CredentialClaim> result = new ArrayList<>();
        if (vc.getCredentialSubject() == null) return result;
        for (Claim claim : vc.getCredentialSubject().getClaims()) {
            String value = claim.getValue();
            boolean isImage = (value != null && value.startsWith("data:image"))
                    || claim.getType() == ClaimType.CLAIM_TYPE.image;
            String display = isImage ? "(Image not displayed)" : (value != null ? value : "");
            result.add(CredentialClaim.flat(claim.getCaption(), display));
        }
        return result;
    }

    @NonNull
    private List<CredentialClaim> mapZkpClaims(@NonNull org.omnione.did.sdk.datamodel.zkp.Credential zkpCred,
                                               @NonNull CredentialSchema schema) {
        List<CredentialClaim> result = new ArrayList<>();
        if (schema.getAttrTypes() == null || zkpCred.getValues() == null) return result;
        for (AttributeType type : schema.getAttrTypes()) {
            if (type.getNamespace() == null) continue;
            String namespace = type.getNamespace().getId();
            for (Map.Entry<String, AttributeValue> entry : zkpCred.getValues().entrySet()) {
                String keyEntry = entry.getKey();
                if (!keyEntry.startsWith(namespace + ".")) continue;
                String label = keyEntry.substring(namespace.length() + 1);
                if (type.getItems() == null) continue;
                for (AttributeDef attrDef : type.getItems()) {
                    if (label.equals(attrDef.getLabel())) {
                        String caption = attrDef.getCaption() != null ? attrDef.getCaption() : attrDef.getLabel();
                        String raw = entry.getValue().getRaw();
                        result.add(CredentialClaim.flat(caption, raw != null ? raw : ""));
                    }
                }
            }
        }
        return result;
    }

    @NonNull
    private List<CredentialClaim> fallbackZkpClaims(@NonNull org.omnione.did.sdk.datamodel.zkp.Credential zkpCred) {
        List<CredentialClaim> result = new ArrayList<>();
        if (zkpCred.getValues() == null) return result;
        for (Map.Entry<String, AttributeValue> entry : zkpCred.getValues().entrySet()) {
            String key = entry.getKey();
            String word = key.substring(key.lastIndexOf('.') + 1);
            String label = word.isEmpty() ? key : Character.toUpperCase(word.charAt(0)) + word.substring(1);
            String raw = entry.getValue() != null ? entry.getValue().getRaw() : null;
            result.add(CredentialClaim.flat(label, raw != null ? raw : ""));
        }
        return result;
    }

    private void logOpenDidVc(int index, int total, @NonNull VerifiableCredential vc) {
        StringBuilder sb = new StringBuilder();
        sb.append("issued VC [").append(index).append('/').append(total).append("] (OpenDID VC)")
                .append(" id=").append(vc.getId())
                .append(", issuer=").append(vc.getIssuer() != null ? vc.getIssuer().getName() : "")
                .append(", schema=").append(vc.getCredentialSchema() != null ? vc.getCredentialSchema().getId() : "")
                .append(", issuanceDate=").append(vc.getIssuanceDate())
                .append(", validUntil=").append(vc.getValidUntil());
        List<CredentialClaim> claims = mapClaims(vc);
        sb.append(", claims={");
        for (int i = 0; i < claims.size(); i++) {
            CredentialClaim c = claims.get(i);
            if (i > 0) sb.append(", ");
            sb.append(c.label).append('=').append(c.value);
        }
        sb.append('}');
        sb.append(", raw=").append(vc.toJson());
        AppLog.d(TAG, sb.toString());
    }

    private void logSdJwtVc(int index, int total, @NonNull SdJwtCredentialItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("issued VC [").append(index).append('/').append(total).append("] (SD-JWT)")
                .append(" id=").append(item.getId())
                .append(", configurationId=").append(item.getConfigurationId());
        SdJwt sdjwt = item.getSdjwt();
        if (sdjwt != null) {
            sb.append(", issuer=").append(sdjwt.getIssuer())
                    .append(", iat=").append(readEpochSeconds(sdjwt, "iat"))
                    .append(", exp=").append(readEpochSeconds(sdjwt, "exp"))
                    .append(", disclosedClaims={");
            boolean first = true;
            for (Map.Entry<String, Object> c : sdjwt.getDisclosedClaims().entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(c.getKey()).append('=').append(c.getValue());
            }
            sb.append('}');
            sb.append(", raw=").append(sdjwt.getCompact());

            String jwt = sdjwt.getCredentialJwt();
            String[] parts = jwt != null ? jwt.split("\\.") : new String[0];
            if (parts.length >= 2) {
                sb.append(", header=").append(decodeJwtSegment(parts[0]));
                sb.append(", payload=").append(decodeJwtSegment(parts[1]));
            }
        } else {
            sb.append(", sdjwt=null");
        }
        AppLog.d(TAG, sb.toString());
    }

    private void logMdoc(int index, int total, @NonNull MdocCredentialItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("issued VC [").append(index).append('/').append(total).append("] (mDoc)")
                .append(" id=").append(item.getId())
                .append(", configurationId=").append(item.getConfigurationId());
        Mdoc mdoc = item.getMdoc();
        if (mdoc != null) {
            MdocValidityInfo validity = mdoc.getMso() != null ? mdoc.getMso().getValidityInfo() : null;
            sb.append(", docType=").append(mdoc.getDocType())
                    .append(", issuerKid=").append(mdoc.getMso() != null ? mdoc.getMso().getIssuerKid() : null)
                    .append(", validFrom=").append(validity != null ? validity.getValidFromEpochSec() : null)
                    .append(", validUntil=").append(validity != null ? validity.getValidUntilEpochSec() : null)
                    .append(", status=").append(describeStatusRef(item.getRawCredential()))
                    .append(", nameSpaces={");
            boolean firstNs = true;
            for (Map.Entry<String, Map<String, Object>> ns : mdoc.getNameSpaces().entrySet()) {
                if (!firstNs) sb.append(", ");
                firstNs = false;
                sb.append(ns.getKey()).append(':').append(ns.getValue().keySet());
            }
            sb.append('}');
        } else {
            sb.append(", mdoc=null");
        }
        AppLog.d(TAG, sb.toString());
    }

    @NonNull
    private static String describeStatusRef(@Nullable String rawCredential) {
        StatusListReference listRef = MdocStatusListReader.parse(rawCredential);
        return listRef != null ? listRef.uri + "#" + listRef.idx : "none";
    }

    @NonNull
    private CompletableFuture<List<MappedCredential>> appendOid4vciCredentials(
            @NonNull List<MappedCredential> base,
            @NonNull List<OID4VCCredentialItem> items) {
        if (items.isEmpty()) {
            oid4vcIds = Collections.emptySet();
            return CompletableFuture.completedFuture(base);
        }
        Set<String> ids = new HashSet<>();
        List<MappedCredential> merged = new ArrayList<>(base);
        for (int i = 0; i < items.size(); i++) {
            OID4VCCredentialItem item = items.get(i);
            ids.add(item.getId());
            if (item instanceof MdocCredentialItem) {
                MdocCredentialItem mdocItem = (MdocCredentialItem) item;
                logMdoc(i + 1, items.size(), mdocItem);
                merged.add(mapMdocItem(mdocItem));
            } else if (item instanceof SdJwtCredentialItem) {
                SdJwtCredentialItem sdJwtItem = (SdJwtCredentialItem) item;
                logSdJwtVc(i + 1, items.size(), sdJwtItem);
                merged.add(mapOid4vciItem(sdJwtItem));
            } else {
                AppLog.w(TAG, "unsupported OID4VC item type: " + item.getClass().getName());
            }
        }
        oid4vcIds = ids;
        return CompletableFuture.completedFuture(merged);
    }

    @NonNull
    private MappedCredential mapOid4vciItem(@NonNull SdJwtCredentialItem item) {
        String issuer = "";
        String issued = null;
        String valid = null;
        SdJwt sdjwt = item.getSdjwt();
        if (sdjwt != null) {
            if (sdjwt.getIssuer() != null) issuer = sdjwt.getIssuer();
            issued = formatEpochSeconds(sdjwt, "iat");
            valid = formatEpochSeconds(sdjwt, "exp");
        }
        StatusListReference listRef =
                sdjwt != null ? StatusListReference.parse(sdjwt.getJwtPayload()) : null;
        Long expSec = sdjwt != null ? readEpochSeconds(sdjwt, "exp") : null;
        return new MappedCredential(
                new Credential(
                        item.getId(),
                        item.getConfigurationId(),
                        CredentialBadge.SD_JWT,
                        issuer,
                        issued,
                        valid,
                        lastKnownStatus(item.getId(), expSec),
                        false),
                CredentialStatusRefs.fromSdJwtItem(item));
    }

    @NonNull
    private MappedCredential mapMdocItem(@NonNull MdocCredentialItem item) {
        Mdoc mdoc = item.getMdoc();
        MdocMso mso = mdoc != null ? mdoc.getMso() : null;
        MdocValidityInfo validity = mso != null ? mso.getValidityInfo() : null;

        String issuer = mso != null && mso.getIssuerDid() != null ? mso.getIssuerDid() : "";
        Long validFromSec = validity != null ? validity.getValidFromEpochSec() : null;
        Long validUntilSec = validity != null ? validity.getValidUntilEpochSec() : null;
        StatusListReference listRef = MdocStatusListReader.parse(item.getRawCredential());

        return new MappedCredential(
                new Credential(
                        item.getId(),
                        item.getConfigurationId(),
                        CredentialBadge.MDOC,
                        issuer,
                        formatEpochSeconds(validFromSec),
                        formatEpochSeconds(validUntilSec),
                        lastKnownStatus(item.getId(), validUntilSec),
                        false),
                CredentialStatusRefs.fromMdocItem(item));
    }

    @NonNull
    private VcDetailResult mapOid4vciDetail(@NonNull List<OID4VCCredentialItem> items) {
        if (items.isEmpty()) {
            throw new CompletionException(new IllegalStateException("OID4VCI VC not found"));
        }
        OID4VCCredentialItem item = items.get(0);
        if (item instanceof MdocCredentialItem) {
            return mapMdocDetail((MdocCredentialItem) item);
        }
        if (!(item instanceof SdJwtCredentialItem)) {
            throw new CompletionException(
                    new IllegalStateException("unsupported OID4VC item: " + item.getClass().getName()));
        }
        SdJwtCredentialItem sdJwtItem = (SdJwtCredentialItem) item;

        MappedCredential header = mapOid4vciItem(sdJwtItem);
        List<CredentialClaim> claims = new ArrayList<>();
        SdJwt sdjwt = sdJwtItem.getSdjwt();
        if (sdjwt != null) {
            for (Map.Entry<String, Object> c : sdjwt.getDisclosedClaims().entrySet()) {
                claims.add(buildOid4vciClaim(c.getKey(), c.getValue()));
            }
        }
        return new VcDetailResult(header.credential, claims, null, header.statusRef);
    }

    @NonNull
    private VcDetailResult mapMdocDetail(@NonNull MdocCredentialItem item) {
        MappedCredential header = mapMdocItem(item);
        List<CredentialClaim> claims = new ArrayList<>();
        Mdoc mdoc = item.getMdoc();
        if (mdoc != null) {
            Map<String, Map<String, Object>> nameSpaces = mdoc.getNameSpaces();
            boolean flatten = nameSpaces.size() <= 1;
            for (Map.Entry<String, Map<String, Object>> ns : nameSpaces.entrySet()) {
                if (flatten) {
                    for (Map.Entry<String, Object> element : ns.getValue().entrySet()) {
                        claims.add(buildOid4vciClaim(element.getKey(), element.getValue()));
                    }
                } else {
                    List<CredentialClaim> grouped = new ArrayList<>();
                    for (Map.Entry<String, Object> element : ns.getValue().entrySet()) {
                        grouped.add(buildOid4vciClaim(element.getKey(), element.getValue()));
                    }
                    if (!grouped.isEmpty()) {
                        claims.add(CredentialClaim.group(ns.getKey(), grouped));
                    }
                }
            }
        }
        return new VcDetailResult(header.credential, claims, null, header.statusRef);
    }

    @NonNull
    private static CredentialClaim buildOid4vciClaim(@NonNull String label, @Nullable Object value) {
        if (value instanceof Map) {
            List<CredentialClaim> subItems = new ArrayList<>();
            for (Map.Entry<?, ?> sub : ((Map<?, ?>) value).entrySet()) {
                subItems.add(buildOid4vciLeafClaim(String.valueOf(sub.getKey()), sub.getValue()));
            }
            if (!subItems.isEmpty()) {
                return CredentialClaim.group(label, subItems);
            }
        } else if (value instanceof List) {
            List<CredentialClaim> subItems = new ArrayList<>();
            for (Object el : (List<?>) value) {
                if (el instanceof Map) {
                    for (Map.Entry<?, ?> f : ((Map<?, ?>) el).entrySet()) {
                        subItems.add(buildOid4vciLeafClaim(String.valueOf(f.getKey()), f.getValue()));
                    }
                } else {
                    subItems.add(buildOid4vciLeafClaim("", el));
                }
            }
            if (!subItems.isEmpty()) {
                return CredentialClaim.group(label, subItems);
            }
        }
        return buildOid4vciLeafClaim(label, value);
    }

    @NonNull
    private static CredentialClaim buildOid4vciLeafClaim(@NonNull String label,
                                                         @Nullable Object value) {
        String text = leafText(value);
        if (ClaimImages.isImageElement(label)) {
            byte[] imageBytes = ClaimImages.toImageBytes(value);
            if (imageBytes != null) {
                return CredentialClaim.image(label, imageBytes, text);
            }
        }
        return CredentialClaim.flat(label, text);
    }

    @NonNull
    private static String leafText(@Nullable Object value) {
        if (value instanceof byte[]) {
            return "(" + ((byte[]) value).length + " bytes)";
        }
        return value == null ? "" : String.valueOf(value);
    }

    @Nullable
    private static String decodeJwtSegment(@NonNull String segment) {
        try {
            byte[] json = java.util.Base64.getUrlDecoder().decode(segment);
            return new String(json, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String formatEpochSeconds(@NonNull SdJwt parsed, @NonNull String claim) {
        if (!parsed.getJwtPayload().has(claim)) return null;
        try {
            return formatEpochSeconds(parsed.getJwtPayload().get(claim).getAsLong());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String formatEpochSeconds(@Nullable Long epochSec) {
        if (epochSec == null) return null;
        try {
            java.text.SimpleDateFormat out =
                    new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(new java.util.Date(epochSec * 1000L));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Long readEpochSeconds(@NonNull SdJwt parsed, @NonNull String claim) {
        if (!parsed.getJwtPayload().has(claim)) return null;
        try {
            return parsed.getJwtPayload().get(claim).getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static final class WalletLoad {
        @NonNull public final List<Credential> credentials;
        public final boolean statusCheckFailed;

        WalletLoad(@NonNull List<Credential> credentials, boolean statusCheckFailed) {
            this.credentials = credentials;
            this.statusCheckFailed = statusCheckFailed;
        }
    }

    public static final class Oid4vcRevokeTarget {
        @NonNull public final IssuerMetadataResponse metadata;
        @Nullable public final String kid;

        Oid4vcRevokeTarget(@NonNull IssuerMetadataResponse metadata, @Nullable String kid) {
            this.metadata = metadata;
            this.kid = kid;
        }
    }

    private static final class MappedCredential {
        @NonNull final Credential credential;
        @Nullable final CredentialStatusRef statusRef;

        MappedCredential(@NonNull Credential credential, @Nullable CredentialStatusRef statusRef) {
            this.credential = credential;
            this.statusRef = statusRef;
        }
    }
}
