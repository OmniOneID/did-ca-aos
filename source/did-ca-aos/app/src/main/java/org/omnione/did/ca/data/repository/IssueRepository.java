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

import org.omnione.did.ca.BuildConfig;
import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.model.IssuableCredential;
import org.omnione.did.ca.data.model.IssueOffer;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.PayloadData;
import org.omnione.did.ca.data.network.protocol.ConfirmIssueVcOp;
import org.omnione.did.ca.data.network.protocol.ConfirmIssueVcProxyOp;
import org.omnione.did.ca.data.network.protocol.GetVcPlanListOp;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.network.protocol.IssueRequestCreateTokenOp;
import org.omnione.did.ca.data.network.protocol.IssueRequestEcdhOp;
import org.omnione.did.ca.data.network.protocol.ProposeIssueVcOp;
import org.omnione.did.ca.data.network.protocol.ProposeIssueVcProxyOp;
import org.omnione.did.ca.data.network.protocol.RequestAttestedAppInfoOp;
import org.omnione.did.ca.data.network.protocol.RequestIssueProfileOp;
import org.omnione.did.ca.data.network.protocol.RequestIssueProfileProxyOp;
import org.omnione.did.ca.data.network.protocol.RequestVcSchemaOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.offer.IssueOfferPayload;
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.issue.VCPlan;
import org.omnione.did.sdk.datamodel.vc.issue.VCPlanList;
import org.omnione.did.sdk.datamodel.vc.issue.VcIssuanceMode;
import org.omnione.did.sdk.datamodel.vcschema.VCSchema;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class IssueRepository {

    private final Context appContext;
    private final NetworkManager net;
    private final AppConfig config;
    private final WalletGateway walletGateway;
    private final UserIdentityRepository identity;
    private final ProxyEndpointRepository proxyEndpoints;
    private final WalletApi walletApi;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private volatile SdkSession session;

    private static final class SdkSession {
        final String vcPlanId;
        final String issuerDid;
        final String issuanceMode;
        @Nullable final String proxyBaseUrl;
        @Nullable String offerId;
        String txId, refId, hWalletToken, serverToken;
        byte[] clientNonce;
        org.omnione.did.sdk.utility.DataModels.EcKeyPair dhKeyPair;
        IssueProfile profile;
        String authNonce;
        DIDAuth signedDIDAuth;

        SdkSession(String vcPlanId, String issuerDid, String issuanceMode,
                   @Nullable String proxyBaseUrl) {
            this.vcPlanId = vcPlanId;
            this.issuerDid = issuerDid;
            this.issuanceMode = issuanceMode;
            this.proxyBaseUrl = proxyBaseUrl;
        }

        boolean isProxy() { return IssuableCredential.MODE_PROXY.equalsIgnoreCase(issuanceMode); }
    }

    @Inject
    public IssueRepository(@ApplicationContext Context appContext,
                                  NetworkManager net,
                                  AppConfig config,
                                  WalletGateway walletGateway,
                                  UserIdentityRepository identity,
                                  ProxyEndpointRepository proxyEndpoints,
                                  WalletApi walletApi) {
        this.appContext = appContext;
        this.net = net;
        this.config = config;
        this.walletGateway = walletGateway;
        this.identity = identity;
        this.proxyEndpoints = proxyEndpoints;
        this.walletApi = walletApi;
    }

        public CompletableFuture<List<IssuableCredential>> listIssuableCredentials() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = GetVcPlanListOp.call(net, config.tasUrl());
                VCPlanList list = MessageUtil.deserialize(body, VCPlanList.class);
                List<IssuableCredential> result = new ArrayList<>();
                if (list.getItems() != null) {
                    for (VCPlan plan : list.getItems()) {
                        result.add(toIssuableCredential(plan));
                    }
                }
                return result;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

    private static IssuableCredential toIssuableCredential(VCPlan plan) {
        String allowedIssuer = (plan.getAllowedIssuers() != null
                && !plan.getAllowedIssuers().isEmpty())
                ? plan.getAllowedIssuers().get(0)
                : "";
        VcIssuanceMode modeEnum = plan.getIssuanceMode();
        boolean proxy = modeEnum == VcIssuanceMode.PROXY;
        String[] endpoints = plan.getEndpoints();
        String proxyEndpoint = (proxy && endpoints != null && endpoints.length > 0)
                ? endpoints[0]
                : null;
        return new IssuableCredential(
                plan.getVcPlanId() != null ? plan.getVcPlanId() : "",
                allowedIssuer,
                plan.getName() != null ? plan.getName() : "",
                plan.getDescription() != null ? plan.getDescription() : "",
                deriveIssuerName(plan),
                proxy ? IssuableCredential.MODE_PROXY : IssuableCredential.MODE_DIRECT,
                proxyEndpoint,

                CredentialBadge.VC);
    }

    private static String deriveIssuerName(VCPlan plan) {
        return "";
    }

        public CompletableFuture<IssueOffer> parseOffer(@NonNull String rawPayload) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                PayloadData payloadData = MessageUtil.deserialize(rawPayload, PayloadData.class);
                if (payloadData == null
                        || !PayloadData.TYPE_ISSUE_VC.equals(payloadData.getPayloadType())) {
                    throw new IllegalArgumentException("Not an issue offer");
                }
                String inner = new String(MultibaseUtils.decode(payloadData.getPayload()));
                IssueOfferPayload offer = MessageUtil.deserialize(inner, IssueOfferPayload.class);
                if (offer == null
                        || offer.getOfferId() == null
                        || offer.getVcPlanId() == null
                        || offer.getIssuer() == null) {
                    throw new IllegalArgumentException("Incomplete issue offer payload");
                }
                return new IssueOffer(offer.getOfferId(), offer.getVcPlanId(), offer.getIssuer());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

        public CompletableFuture<String> prepareIssuance(@NonNull IssuableCredential plan) {
        return runPrepareIssuance(plan,  null);
    }

        public CompletableFuture<String> prepareIssuanceFromOffer(@NonNull String offerId,
                                                              @NonNull String vcPlanId,
                                                              @NonNull String issuerDid) {

        return listIssuableCredentials().thenCompose(items -> {
            IssuableCredential match = null;
            for (IssuableCredential c : items) {
                if (vcPlanId.equals(c.vcPlanId)) { match = c; break; }
            }
            IssuableCredential plan = (match != null)
                    ? match
                    : new IssuableCredential(vcPlanId, issuerDid, "", "", "",
                            IssuableCredential.MODE_DIRECT, null, CredentialBadge.VC);
            return runPrepareIssuance(plan, offerId);
        });
    }

    private CompletableFuture<String> runPrepareIssuance(IssuableCredential plan,
                                                         @Nullable String offerId) {
        boolean isProxy = plan.isProxy() && plan.proxyEndpoint != null && !plan.proxyEndpoint.isEmpty();
        SdkSession s = new SdkSession(plan.vcPlanId, plan.allowedIssuerDid,
                isProxy ? IssuableCredential.MODE_PROXY : IssuableCredential.MODE_DIRECT,
                isProxy ? plan.proxyEndpoint : null);
        s.offerId = offerId;
        session = s;
        return isProxy ? runPrepareProxy(s) : runPrepareDirect(s);
    }

    private CompletableFuture<String> runPrepareDirect(SdkSession s) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tas = config.tasUrl();
                String cas = config.casUrl();

                ProposeIssueVcOp.Result propose = ProposeIssueVcOp.call(
                        net, appContext, tas, s.vcPlanId, s.issuerDid, s.offerId);
                s.txId = propose.txId;
                s.refId = propose.refId;

                IssueRequestEcdhOp.Result ecdh = IssueRequestEcdhOp.call(
                        net, appContext, tas, walletApi, s.txId);
                s.clientNonce = ecdh.clientNonce;
                s.dhKeyPair = ecdh.dhKeyPair;

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.ISSUE_VC).get();

                String attestedRaw = RequestAttestedAppInfoOp.call(
                        net, cas, identity.getOrCreateCaAppId());
                AttestedAppInfo attested = MessageUtil.deserialize(
                        attestedRaw, AttestedAppInfo.class);

                ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
                serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.ISSUE_VC);
                SignedWalletInfo signedWalletInfo = walletGateway.getSignedWalletInfo().get();
                serverTokenSeed.setWalletInfo(signedWalletInfo);
                serverTokenSeed.setCaAppInfo(attested);
                String createTokenResp = IssueRequestCreateTokenOp.call(
                        net, appContext, tas, s.txId, serverTokenSeed);
                s.serverToken = TokenUtil.createServerToken(
                        createTokenResp, ecdh.rawResponseJson, s.clientNonce, s.dhKeyPair);

                RequestIssueProfileOp.Result profileResult = RequestIssueProfileOp.call(
                        net, appContext, tas, s.txId, s.serverToken);
                s.profile = profileResult.profile;
                s.authNonce = profileResult.authNonce;

                return resolveSchemaTitle(s.profile);
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private CompletableFuture<String> runPrepareProxy(SdkSession s) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String proxyUrl = s.proxyBaseUrl;

                ProposeIssueVcProxyOp.Result propose = ProposeIssueVcProxyOp.call(
                        net, appContext, proxyUrl, s.vcPlanId, s.issuerDid, s.offerId);
                s.txId = propose.txId;
                s.refId = propose.refId;

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.ISSUE_VC).get();

                RequestIssueProfileProxyOp.Result profileResult = RequestIssueProfileProxyOp.call(
                        net, appContext, proxyUrl, s.txId, identity.getUserId());
                s.profile = profileResult.profile;
                s.authNonce = profileResult.authNonce;
                s.serverToken = null;

                return resolveSchemaTitle(s.profile);
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private String resolveSchemaTitle(IssueProfile profile) {
        if (profile == null) return "";
        String schemaUrl = extractSchemaUrl(profile);
        if (schemaUrl == null || schemaUrl.isEmpty()) return "";
        try {
            String body = RequestVcSchemaOp.call(net, schemaUrl);
            VCSchema schema = MessageUtil.deserialize(body, VCSchema.class);
            String t = schema.getTitle();
            return t != null ? t : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractSchemaUrl(IssueProfile profile) {
        try {
            if (profile.getProfile() == null) return "";
            if (profile.getProfile().credentialSchema == null) return "";
            return profile.getProfile().credentialSchema.id;
        } catch (Exception e) {
            return "";
        }
    }

        public CompletableFuture<Void> signWithPin(@NonNull String pin) {
        SdkSession s = session;
        if (s == null) return failedSession();
        return walletGateway.getSignedDIDAuth(s.authNonce, pin)
                .thenAccept(signed -> s.signedDIDAuth = signed);
    }

        public CompletableFuture<Void> signWithBio() {
        SdkSession s = session;
        if (s == null) return failedSession();
        return walletGateway.getSignedDIDAuth(s.authNonce,  null)
                .thenCompose(unsigned -> {
                    try {
                        String holderDid = walletGateway.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).get().getId();
                        return walletGateway.addProofsToDocument(
                                unsigned,
                                List.of(Constants.KEY_ID_BIO),
                                holderDid,
                                Constants.DID_DOC_TYPE_HOLDER,
                                 null,
                                 true);
                    } catch (Exception e) {
                        CompletableFuture<org.omnione.did.sdk.datamodel.common.ProofContainer> f =
                                new CompletableFuture<>();
                        f.completeExceptionally(e);
                        return f;
                    }
                })
                .thenAccept(signed -> s.signedDIDAuth = (DIDAuth) signed);
    }

        public CompletableFuture<String> completeIssuance() {
        SdkSession s = session;
        if (s == null) return failedSession();
        boolean isProxy = s.isProxy();

        String issueBaseUrl = isProxy
                ? s.proxyBaseUrl + "/proxy"
                : config.tasUrl() + "/tas";
        return walletGateway.requestIssueVc(
                        s.hWalletToken,
                        issueBaseUrl,
                        config.apiGwUrl(),
                        s.serverToken,
                        s.refId,
                        s.profile,
                        s.signedDIDAuth,
                        s.txId)
                .thenCompose(vcId -> CompletableFuture.supplyAsync(() -> {
                    try {

                        if (isProxy) {
                            proxyEndpoints.put(vcId, s.proxyBaseUrl);
                            ConfirmIssueVcProxyOp.call(net, appContext, s.proxyBaseUrl,
                                    s.txId, vcId);
                        } else {
                            ConfirmIssueVcOp.call(net, appContext, config.tasUrl(),
                                    s.txId, s.serverToken, vcId);
                        }
                        return vcId;
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, backgroundExecutor))
                .thenApply(vcId -> {
                    session = null;
                    return vcId;
                });
    }

        public void cancelIssuance() {
        session = null;
    }

        @Nullable
    public String getClaimWebUrl() {
        String url = computeClaimWebUrl();
        AppLog.d("ClaimWebUrl", String.valueOf(url));
        return url;
    }

    @Nullable
    private String computeClaimWebUrl() {
        SdkSession s = session;
        if (s == null || s.profile == null) return null;

        String schemaUrl = extractSchemaUrl(s.profile);
        if (schemaUrl == null || schemaUrl.isEmpty()) return null;

        int idx = schemaUrl.indexOf("name=");
        if (idx == -1) return null;
        int start = idx + "name=".length();
        int end = schemaUrl.indexOf('&', start);
        String vcSchemaName = (end == -1)
                ? schemaUrl.substring(start)
                : schemaUrl.substring(start, end);

        String holderDid;
        try {
            holderDid = walletGateway.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).get().getId();
        } catch (Exception e) {
            return null;
        }

        String userId = identity.getUserId();
        if (userId == null) userId = "";

        return BuildConfig.DEMO_URL
                + "/addVcInfo?did=" + holderDid
                + "&userName=" + userId
                + "&vcSchemaId=" + vcSchemaName;
    }

    private static <T> CompletableFuture<T> failedSession() {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(new IllegalStateException("no session"));
        return f;
    }
}
