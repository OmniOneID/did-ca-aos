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
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.ConfirmRevokeVcOp;
import org.omnione.did.ca.data.network.protocol.ConfirmRevokeVcProxyOp;
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.network.protocol.ProposeRevokeVcOp;
import org.omnione.did.ca.data.network.protocol.ProposeRevokeVcProxyOp;
import org.omnione.did.ca.data.network.protocol.RequestAttestedAppInfoOp;
import org.omnione.did.ca.data.network.protocol.RevokeRequestCreateTokenOp;
import org.omnione.did.ca.data.network.protocol.RevokeRequestEcdhOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class RevokeRepository {

    private final Context appContext;
    private final NetworkManager net;
    private final AppConfig config;
    private final WalletGateway walletGateway;
    private final UserIdentityRepository identity;
    private final ProxyEndpointRepository proxyEndpoints;
    private final WalletApi walletApi;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    private volatile SdkSession session;

    private static final class SdkSession {
        final String vcId;
        final boolean isProxy;

        final String baseUrl;

        @Nullable final String proxyEndpoint;
        String txId;
        String issuerNonce;
        VerifyAuthType.VERIFY_AUTH_TYPE authType;
        String hWalletToken;
        @Nullable String serverToken;

        SdkSession(String vcId, boolean isProxy, String baseUrl, @Nullable String proxyEndpoint) {
            this.vcId = vcId;
            this.isProxy = isProxy;
            this.baseUrl = baseUrl;
            this.proxyEndpoint = proxyEndpoint;
        }
    }

    @Inject
    public RevokeRepository(@ApplicationContext Context appContext,
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

    @NonNull
        public CompletableFuture<VerifyAuthType.VERIFY_AUTH_TYPE> prepareRevoke(@NonNull String vcId) {
        String proxyEndpoint = proxyEndpoints.get(vcId);
        boolean isProxy = proxyEndpoint != null && !proxyEndpoint.isEmpty();
        String baseUrl = isProxy
                ? proxyEndpoint + "/proxy"
                : config.tasUrl() + "/tas";
        SdkSession s = new SdkSession(vcId, isProxy, baseUrl, isProxy ? proxyEndpoint : null);
        session = s;
        return isProxy ? runPrepareProxy(s) : runPrepareDirect(s);
    }

    private CompletableFuture<VerifyAuthType.VERIFY_AUTH_TYPE> runPrepareDirect(SdkSession s) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tas = config.tasUrl();
                String cas = config.casUrl();

                ProposeRevokeVcOp.Result propose = ProposeRevokeVcOp.call(
                        net, appContext, tas, s.vcId);
                s.txId = propose.txId;
                s.issuerNonce = propose.issuerNonce;
                s.authType = propose.authType;

                RevokeRequestEcdhOp.Result ecdh = RevokeRequestEcdhOp.call(
                        net, appContext, tas, walletApi, s.txId);

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC).get();

                String attestedRaw = RequestAttestedAppInfoOp.call(
                        net, cas, identity.getOrCreateCaAppId());
                AttestedAppInfo attested = MessageUtil.deserialize(
                        attestedRaw, AttestedAppInfo.class);

                ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
                serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.REVOKE_VC);
                SignedWalletInfo signedWalletInfo = walletGateway.getSignedWalletInfo().get();
                serverTokenSeed.setWalletInfo(signedWalletInfo);
                serverTokenSeed.setCaAppInfo(attested);
                String createTokenResp = RevokeRequestCreateTokenOp.call(
                        net, appContext, tas, s.txId, serverTokenSeed);
                s.serverToken = TokenUtil.createServerToken(
                        createTokenResp, ecdh.rawResponseJson, ecdh.clientNonce, ecdh.dhKeyPair);

                return s.authType;
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    private CompletableFuture<VerifyAuthType.VERIFY_AUTH_TYPE> runPrepareProxy(SdkSession s) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                ProposeRevokeVcProxyOp.Result propose = ProposeRevokeVcProxyOp.call(
                        net, appContext, s.proxyEndpoint, s.vcId);
                s.txId = propose.txId;
                s.issuerNonce = propose.issuerNonce;
                s.authType = propose.authType;

                s.hWalletToken = GetWalletTokenOp.call(
                        walletGateway, net, config, appContext,
                        identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC).get();

                s.serverToken = null;
                return s.authType;
            } catch (Throwable t) {
                session = null;
                throw new CompletionException(t);
            }
        }, backgroundExecutor);
    }

    @NonNull
        public CompletableFuture<Void> completeRevokeWithPin(@NonNull String pin) {
        return runComplete(pin);
    }

    @NonNull
        public CompletableFuture<Void> completeRevokeWithBio() {

        return runComplete("");
    }

    private CompletableFuture<Void> runComplete(String pin) {
        SdkSession s = session;
        if (s == null) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("no revoke session"));
            return f;
        }
        VerifyAuthType.VERIFY_AUTH_TYPE sdkAuthType = pin.isEmpty()
                ? VerifyAuthType.VERIFY_AUTH_TYPE.BIO
                : VerifyAuthType.VERIFY_AUTH_TYPE.PIN;
        return walletGateway.requestRevokeVc(
                        s.hWalletToken,
                        s.baseUrl,
                        s.serverToken,
                        s.txId,
                        s.vcId,
                        s.issuerNonce,
                        pin,
                        sdkAuthType)
                .thenCompose(unused -> CompletableFuture.supplyAsync(() -> {
                    try {
                        if (s.isProxy) {
                            ConfirmRevokeVcProxyOp.call(net, appContext, s.proxyEndpoint, s.txId);
                        } else {
                            ConfirmRevokeVcOp.call(net, appContext, config.tasUrl(),
                                    s.txId, s.serverToken);
                        }
                        return null;
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, backgroundExecutor))
                .thenCompose(unused -> walletGateway.deleteCredentials(s.hWalletToken, s.vcId))
                .thenApply(unused -> {
                    if (s.isProxy) {
                        proxyEndpoints.remove(s.vcId);
                    }
                    session = null;
                    return null;
                });
    }

        public void cancelRevoke() {
        session = null;
    }
}
