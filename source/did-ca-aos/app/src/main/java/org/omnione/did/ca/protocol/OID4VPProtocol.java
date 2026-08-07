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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.oid4vc.crypto.JwsParts;
import org.omnione.did.sdk.core.oid4vc.verify.Es256Verifier;
import org.omnione.did.sdk.datamodel.oid4vc.AuthorizationRequest;
import org.omnione.did.sdk.datamodel.oid4vc.MatchedCredential;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class OID4VPProtocol {

    @NonNull private final WalletApi walletApi;
    @NonNull private final HttpUrlConnectionTask http;

    @Inject
    public OID4VPProtocol(@NonNull WalletApi walletApi) {
        this.walletApi = walletApi;
        this.http = new HttpUrlConnectionTask();
    }

    public CompletableFuture<AuthorizationRequest> getAuthorizationRequest(@NonNull String rawUri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestUri = extractRequestUri(rawUri);
                AppLog.d("OID4VPProtocol: getAuthorizationRequest requestUri=" + requestUri);
                String jws = http.makeHttpRequest(requestUri, "GET", null, null);

                JwsParts parts = JwsParts.parse(jws);
                JsonObject header = JsonParser.parseString(parts.headerDecoded()).getAsJsonObject();
                JsonObject jwk = header.getAsJsonObject("jwk");
                if (jwk == null) {
                    throw new SecurityException(
                            "JWS header missing jwk (header=" + parts.headerDecoded() + ")");
                }
                if (!Es256Verifier.verify(parts, jwk)) {
                    String alg = header.get("alg") != null ? header.get("alg").getAsString() : "?";
                    String crv = jwk.get("crv") != null ? jwk.get("crv").getAsString() : "?";
                    throw new SecurityException("JWS signature verification failed"
                            + " (alg=" + alg + ", crv=" + crv
                            + ", sigLen=" + parts.signatureBytes().length
                            + ", headerB64Len=" + parts.header().length()
                            + ", payloadB64Len=" + parts.payload().length() + ")");
                }

                AppLog.d("OID4VPProtocol: requestObject payload=" + parts.payloadDecoded());
                AuthorizationRequest ar =
                        MessageUtil.deserialize(parts.payloadDecoded(), AuthorizationRequest.class);
                if (ar == null) {
                    throw new IllegalStateException(
                            "Failed to parse AuthorizationRequest from request object payload");
                }
                return ar;
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<List<MatchedCredential>> matchCredentials(
            @NonNull String hWalletToken, @NonNull AuthorizationRequest ar) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d("OID4VPProtocol: matchCredentials clientId=" + ar.getClientId());
                return walletApi.matchCredentials(hWalletToken, ar);
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<byte[]> createVpToken(@NonNull String hWalletToken,
                                                   @NonNull AuthorizationRequest ar,
                                                   @NonNull List<MatchedCredential> matched,
                                                   @Nullable String passcode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d("OID4VPProtocol: createVpToken matchedCount=" + matched.size());
                return walletApi.createVpToken(hWalletToken, ar, matched, passcode);
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<Void> submit(@NonNull AuthorizationRequest ar,
                                          @NonNull byte[] responseBody) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = new String(responseBody, StandardCharsets.UTF_8);
                AppLog.d("OID4VPProtocol: submit responseUri=" + ar.getResponseUri());
                http.makeHttpRequest(ar.getResponseUri(), "POST", body, null,
                        "application/x-www-form-urlencoded");
                return null;
            } catch (Exception e) {
                throw rethrow(e);
            }
        });
    }

    private static String extractRequestUri(String rawUri) {
        if (rawUri == null) {
            throw new IllegalArgumentException("rawUri is null");
        }
        URI uri;
        try {
            uri = URI.create(rawUri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid OID4VP URL: " + rawUri, e);
        }
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("No query in OID4VP URL: " + rawUri);
        }
        try {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                if ("request_uri".equals(key)) {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
            }
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 unsupported", e);
        }
        throw new IllegalArgumentException("request_uri param not found in: " + rawUri);
    }

    @NonNull
    private static RuntimeException rethrow(@NonNull Throwable e) {
        Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        return new CompletionException(cause);
    }
}
