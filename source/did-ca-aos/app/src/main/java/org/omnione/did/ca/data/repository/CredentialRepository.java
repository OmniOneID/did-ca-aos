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
import org.omnione.did.ca.data.statuslist.StatusListReference;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CredentialDateFormatter;
import org.omnione.did.sdk.datamodel.common.enums.ClaimType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
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
    private final ProxyEndpointRepository proxyEndpoints;
    private final CredentialStatusRepository statusRepo;
    private final Context appCtx;
    private final Executor executor = Executors.newCachedThreadPool();

    private volatile Set<String> oid4vcIds = Collections.emptySet();

    private volatile CompletableFuture<List<Credential>> inFlightFetchWallet;

    @Inject
    public CredentialRepository(WalletGateway gateway,
                                       NetworkManager net,
                                       AppConfig config,
                                       UserIdentityRepository identity,
                                       ProxyEndpointRepository proxyEndpoints,
                                       CredentialStatusRepository statusRepo,
                                       @ApplicationContext Context appCtx) {
        this.gateway = gateway;
        this.net = net;
        this.config = config;
        this.identity = identity;
        this.proxyEndpoints = proxyEndpoints;
        this.statusRepo = statusRepo;
        this.appCtx = appCtx;
    }

    @NonNull
        public synchronized CompletableFuture<List<Credential>> fetchWallet() {
        CompletableFuture<List<Credential>> existing = inFlightFetchWallet;
        if (existing != null && !existing.isDone()) {
            AppLog.d(TAG, "fetchWallet: in-flight, returning existing future");
            return existing;
        }
        CompletableFuture<List<Credential>> f = GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC)
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
        if (oid4vcIds.contains(vcId)) {

            return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                            WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC)
                    .thenCompose(token -> gateway.getOID4VCs(token, List.of(vcId)))
                    .thenApply(this::mapOid4vciDetail);
        }
        return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.DETAIL_VC)
                .thenCompose(token -> gateway.getCredential(token, vcId)
                        .thenCompose(vc -> mapDetail(vc, vcId, token)));
    }

    @NonNull
        public CompletableFuture<Void> delete(@NonNull String vcId) {
        if (oid4vcIds.contains(vcId)) {
            return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                            WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC)
                    .thenCompose(token -> gateway.deleteOID4VCs(token, List.of(vcId)))
                    .thenApply(v -> {
                        Set<String> next = new HashSet<>(oid4vcIds);
                        next.remove(vcId);
                        oid4vcIds = next;
                        return v;
                    });
        }
        return GetWalletTokenOp.call(gateway, net, config, appCtx, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC)
                .thenCompose(token -> gateway.deleteCredentials(token, vcId))
                .thenApply(v -> {
                    proxyEndpoints.remove(vcId);
                    return v;
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
        CompletableFuture<VCSchema> schemaFut = CompletableFuture.supplyAsync(() -> {
            try {
                String body = RequestVcSchemaOp.call(net, schemaUrl);
                return MessageUtil.deserialize(body, VCSchema.class);
            } catch (Exception e) {
                AppLog.e(TAG, "mapOne: schema fetch failed for " + schemaUrl, e);
                throw new CompletionException(e);
            }
        }, executor);
        CompletableFuture<Boolean> zkpFut = gateway.isZkpCredentialsSaved(vc.getId());

        Long expSec = CredentialDateFormatter.toEpochSeconds(vc.getValidUntil());
        CredentialStatusRef statusRef = CredentialStatusRef.openDid(vc.getId(), expSec);
        return schemaFut.thenCombine(zkpFut, AbstractMap.SimpleImmutableEntry::new)
                .thenApply(entry -> new MappedCredential(
                        new Credential(
                                vc.getId(),
                                entry.getKey().getTitle() != null ? entry.getKey().getTitle() : "",
                                CredentialBadge.VC,
                                vc.getIssuer() != null ? vc.getIssuer().getName() : "",
                                CredentialDateFormatter.format(vc.getIssuanceDate()),
                                CredentialDateFormatter.format(vc.getValidUntil()),
                                lastKnownStatus(vc.getId(), expSec),
                                entry.getValue()),
                        statusRef));
    }

    @NonNull
    private CompletableFuture<List<Credential>> refreshStatuses(@NonNull List<MappedCredential> mapped) {
        List<CredentialStatusRef> refs = new ArrayList<>(mapped.size());
        for (MappedCredential m : mapped) {
            if (m.statusRef != null) refs.add(m.statusRef);
        }
        return statusRepo.refreshAll(refs).thenApply(statuses -> {
            List<Credential> result = new ArrayList<>(mapped.size());
            for (MappedCredential m : mapped) {
                VcStatus resolved = statuses.get(m.credential.id);
                result.add(resolved == null || resolved == m.credential.status
                        ? m.credential
                        : m.credential.withStatus(resolved));
            }
            return result;
        });
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
                    .thenCompose(zkpCred -> {
                        String zkpSchemaId = zkpCred.getSchemaId();
                        CompletableFuture<CredentialSchema> zkpSchemaFut = CompletableFuture.supplyAsync(() -> {
                            try {
                                String body = RequestZkpSchemaOp.call(net, config.apiGwUrl(), zkpSchemaId);

                                CredentialSchemaVo vo = MessageUtil.deserialize(body, CredentialSchemaVo.class);
                                String decoded = new String(MultibaseUtils.decode(vo.getCredSchema()));
                                return MessageUtil.deserialize(decoded, CredentialSchema.class);
                            } catch (Exception e) {
                                AppLog.e(TAG, "mapDetail: zkp schema fetch failed for vcId=" + vcId, e);
                                throw new CompletionException(e);
                            }
                        }, executor);
                        return zkpSchemaFut.thenApply(schema -> {
                            List<CredentialClaim> zkpClaims = mapZkpClaims(zkpCred, schema);
                            return new VcDetailResult(c, claims, zkpClaims, statusRef);
                        });
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

    @NonNull
    private CompletableFuture<List<MappedCredential>> appendOid4vciCredentials(
            @NonNull List<MappedCredential> base,
            @NonNull List<SdJwtCredentialItem> items) {
        if (items.isEmpty()) {
            oid4vcIds = Collections.emptySet();
            return CompletableFuture.completedFuture(base);
        }
        Set<String> ids = new HashSet<>();
        List<MappedCredential> merged = new ArrayList<>(base);
        for (int i = 0; i < items.size(); i++) {
            SdJwtCredentialItem item = items.get(i);
            logSdJwtVc(i + 1, items.size(), item);
            ids.add(item.getId());
            merged.add(mapOid4vciItem(item));
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
                CredentialStatusRef.sdJwt(item.getId(), listRef, expSec));
    }

    @NonNull
    private VcDetailResult mapOid4vciDetail(@NonNull List<SdJwtCredentialItem> items) {
        if (items.isEmpty()) {
            throw new CompletionException(new IllegalStateException("OID4VCI VC not found"));
        }
        SdJwtCredentialItem item = items.get(0);

        MappedCredential header = mapOid4vciItem(item);
        List<CredentialClaim> claims = new ArrayList<>();
        SdJwt sdjwt = item.getSdjwt();
        if (sdjwt != null) {
            for (Map.Entry<String, Object> c : sdjwt.getDisclosedClaims().entrySet()) {
                claims.add(buildOid4vciClaim(c.getKey(), c.getValue()));
            }
        }
        return new VcDetailResult(header.credential, claims, null, header.statusRef);
    }

    @NonNull
    private static CredentialClaim buildOid4vciClaim(@NonNull String label, @Nullable Object value) {
        if (value instanceof Map) {
            List<CredentialClaim> subItems = new ArrayList<>();
            for (Map.Entry<?, ?> sub : ((Map<?, ?>) value).entrySet()) {
                Object subVal = sub.getValue();
                subItems.add(CredentialClaim.flat(
                        String.valueOf(sub.getKey()),
                        subVal == null ? "" : String.valueOf(subVal)));
            }
            if (!subItems.isEmpty()) {
                return CredentialClaim.group(label, subItems);
            }
        } else if (value instanceof List) {
            List<CredentialClaim> subItems = new ArrayList<>();
            for (Object el : (List<?>) value) {
                if (el instanceof Map) {
                    for (Map.Entry<?, ?> f : ((Map<?, ?>) el).entrySet()) {
                        Object fv = f.getValue();
                        subItems.add(CredentialClaim.flat(
                                String.valueOf(f.getKey()),
                                fv == null ? "" : String.valueOf(fv)));
                    }
                } else {
                    subItems.add(CredentialClaim.flat("", el == null ? "" : String.valueOf(el)));
                }
            }
            if (!subItems.isEmpty()) {
                return CredentialClaim.group(label, subItems);
            }
        }
        return CredentialClaim.flat(label, value == null ? "" : String.valueOf(value));
    }

    @Nullable
    private static String decodeJwtSegment(@NonNull String segment) {
        try {
            byte[] json = java.util.Base64.getUrlDecoder().decode(segment);
            return new String(json, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String formatEpochSeconds(@NonNull SdJwt parsed, @NonNull String claim) {
        if (!parsed.getJwtPayload().has(claim)) return null;
        try {
            long sec = parsed.getJwtPayload().get(claim).getAsLong();
            java.text.SimpleDateFormat out =
                    new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(new java.util.Date(sec * 1000L));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Long readEpochSeconds(@NonNull SdJwt parsed, @NonNull String claim) {
        if (!parsed.getJwtPayload().has(claim)) return null;
        try {
            return parsed.getJwtPayload().get(claim).getAsLong();
        } catch (Exception e) {
            return null;
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
