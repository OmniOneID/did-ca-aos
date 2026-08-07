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
import org.omnione.did.ca.data.network.protocol.GetWalletTokenOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.protocol.OID4VCIProtocol;
import org.omnione.did.sdk.core.oid4vc.errors.Oid4vciException;
import org.omnione.did.sdk.core.oid4vc.model.CredentialOfferResponse;
import org.omnione.did.sdk.core.oid4vc.model.IssuerMetadataResponse;
import org.omnione.did.sdk.core.oid4vc.model.TokenResponse;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class Oid4vciIssueRepository {

    private final OID4VCIProtocol protocol;
    private final WalletGateway walletGateway;
    private final NetworkManager net;
    private final AppConfig config;
    private final UserIdentityRepository identity;
    private final Context appContext;
    private volatile SdkSession session;

    private static final class SdkSession {
        @Nullable CredentialOfferResponse offer;
        @Nullable IssuerMetadataResponse metadata;
        @Nullable TokenResponse token;
    }

    @Inject
    public Oid4vciIssueRepository(OID4VCIProtocol protocol,
                                         WalletGateway walletGateway,
                                         NetworkManager net,
                                         AppConfig config,
                                         UserIdentityRepository identity,
                                         @ApplicationContext Context appContext) {
        this.protocol = protocol;
        this.walletGateway = walletGateway;
        this.net = net;
        this.config = config;
        this.identity = identity;
        this.appContext = appContext;
    }

        public CompletableFuture<CredentialOfferResponse> parseOffer(@NonNull String rawPayload) {
        SdkSession s = new SdkSession();
        session = s;
        return protocol.getCredentialOffer(rawPayload).thenApply(offer -> {
            s.offer = offer;
            return offer;
        });
    }

        public CompletableFuture<IssuerMetadataResponse> fetchMetadata(@NonNull String issuerUrl) {
        SdkSession s = session;
        if (s == null) return CompletableFuture.failedFuture(new Oid4vciException("No active session"));
        return protocol.getMetadata(issuerUrl).thenApply(metadata -> {
            s.metadata = metadata;
            return metadata;
        });
    }

        public CompletableFuture<Void> requestToken(@Nullable String txCode) {
        SdkSession s = session;
        if (s == null || s.offer == null || s.metadata == null) {
            return CompletableFuture.failedFuture(new Oid4vciException("Offer/metadata not loaded"));
        }
        return protocol.requestTokenByPreAuthorizedCode(s.metadata, s.offer, txCode)
                .thenApply(token -> {
                    s.token = token;
                    return null;
                });
    }

        public CompletableFuture<String> requestAndStoreCredential(@NonNull String configurationId,
                                                               @Nullable String passcode) {
        SdkSession s = session;
        if (s == null || s.metadata == null || s.token == null) {
            return CompletableFuture.failedFuture(new Oid4vciException("Token not obtained"));
        }
        IssuerMetadataResponse metadata = s.metadata;
        TokenResponse token = s.token;

        return GetWalletTokenOp.call(walletGateway, net, config, appContext, identity.getUserId(),
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.ISSUE_VC)
                .thenCompose(hWalletToken -> protocol.requestCredential(
                        hWalletToken, metadata, token, passcode, configurationId,
                         null, config.apiGwUrl()))
                .thenApply(vcId -> {
                    session = null;
                    return vcId;
                });
    }

        public void cancelIssuance() {
        session = null;
    }
}
