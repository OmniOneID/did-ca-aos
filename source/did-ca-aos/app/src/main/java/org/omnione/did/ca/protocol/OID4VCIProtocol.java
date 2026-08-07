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

package org.omnione.did.ca.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.oid4vc.errors.Oid4vciException;
import org.omnione.did.sdk.core.oid4vc.model.CredentialOfferResponse;
import org.omnione.did.sdk.core.oid4vc.model.GrantType;
import org.omnione.did.sdk.core.oid4vc.model.IssuerMetadataResponse;
import org.omnione.did.sdk.core.oid4vc.model.TokenResponse;
import org.omnione.did.sdk.core.oid4vc.net.CredentialOfferFetcher;
import org.omnione.did.sdk.core.oid4vc.net.IssuerMetadataFetcher;
import org.omnione.did.sdk.core.oid4vc.net.TokenRequester;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class OID4VCIProtocol {

    @NonNull private final WalletApi walletApi;
    @NonNull private final CredentialOfferFetcher offerFetcher;
    @NonNull private final IssuerMetadataFetcher metadataFetcher;
    @NonNull private final TokenRequester tokenRequester;

    @Inject
    public OID4VCIProtocol(@NonNull WalletApi walletApi) {
        this.walletApi = walletApi;
        HttpUrlConnectionTask http = new HttpUrlConnectionTask();
        this.offerFetcher = new CredentialOfferFetcher(http);
        this.metadataFetcher = new IssuerMetadataFetcher(http);
        this.tokenRequester = new TokenRequester(http);
    }

    public CompletableFuture<CredentialOfferResponse> getCredentialOffer(@NonNull String rawPayload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d("OID4VCIProtocol: getCredentialOffer=" + rawPayload);
                CredentialOfferResponse offer = offerFetcher.parse(rawPayload);
                GrantType grantType = offer.hasPreAuthorizedCode()
                        ? GrantType.PRE_AUTHORIZED_CODE
                        : GrantType.AUTHORIZATION_CODE;
                AppLog.d("OID4VCIProtocol: getCredentialOffer grantType=" + grantType);
                if (grantType != GrantType.PRE_AUTHORIZED_CODE) {
                    throw new Oid4vciException("Unsupported grant type: " + grantType
                            + " (only pre-authorized_code is supported)");
                }
                return offer;
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<IssuerMetadataResponse> getMetadata(@NonNull String issuerUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d("OID4VCIProtocol: getMetadata=" + issuerUrl);
                return metadataFetcher.fetch(issuerUrl);
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<TokenResponse> requestTokenByPreAuthorizedCode(
            @NonNull IssuerMetadataResponse metadata,
            @NonNull CredentialOfferResponse offer,
            @Nullable String txCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String preAuthorizedCode = offer.getPreAuthorizedCode();
                if (preAuthorizedCode == null) {
                    throw new Oid4vciException("Offer lacks pre-authorized_code");
                }
                AppLog.d("OID4VCIProtocol: requestToken tokenEndpoint=" + metadata.getTokenEndpoint());
                return tokenRequester.request(metadata.getTokenEndpoint(), preAuthorizedCode, txCode,
                        offer.getCredentialConfigurationIds());
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<String> requestCredential(
            @NonNull String hWalletToken,
            @NonNull IssuerMetadataResponse metadata,
            @NonNull TokenResponse token,
            @Nullable String passcode,
            @NonNull String configurationId,
            @Nullable String credentialIdentifier,
            @NonNull String apiGatewayUrl) {
        try {
            AppLog.d("OID4VCIProtocol: requestCredential configurationId=" + configurationId);
            return walletApi.requestIssueOID4VC(hWalletToken, metadata, token, passcode,
                    configurationId, credentialIdentifier, apiGatewayUrl);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(rethrow(e));
        }
    }

    @NonNull
    private static RuntimeException rethrow(@NonNull Throwable e) {

        Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new Oid4vciException(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
    }
}
