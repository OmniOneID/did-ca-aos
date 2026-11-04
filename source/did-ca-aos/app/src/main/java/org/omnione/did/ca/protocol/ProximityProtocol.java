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
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.oid4vc.MdocDeviceResponse;
import org.omnione.did.sdk.datamodel.oid4vc.MdocRequestedDocument;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

@Singleton
public final class ProximityProtocol {

    private static final String TAG = "ProximityProtocol";

    @NonNull private final WalletApi walletApi;

    @Inject
    public ProximityProtocol(@NonNull WalletApi walletApi) {
        this.walletApi = walletApi;
    }

    public CompletableFuture<List<MdocRequestedDocument>> matchMdocRequest(
            @NonNull String hWalletToken,
            @NonNull byte[] deviceRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d(TAG, "matchMdocRequest deviceRequest.length=" + deviceRequest.length);
                List<MdocRequestedDocument> matched =
                        walletApi.matchMdocRequest(hWalletToken, deviceRequest);
                AppLog.d(TAG, "matchMdocRequest ok count="
                        + (matched == null ? -1 : matched.size()));
                return matched;
            } catch (UtilityException | WalletCoreException | WalletException | RuntimeException e) {
                AppLog.e(TAG, "matchMdocRequest failed deviceRequest.length=" + deviceRequest.length
                        + " -> " + describe(e), e);
                throw rethrow(e);
            }
        });
    }

    public CompletableFuture<MdocDeviceResponse> createDeviceResponse(
            @NonNull String hWalletToken,
            @NonNull byte[] deviceRequest,
            @NonNull byte[] sessionTranscript,
            @NonNull List<MdocRequestedDocument> selected,
            @Nullable String passcode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AppLog.d(TAG, "createDeviceResponse deviceRequest.length=" + deviceRequest.length
                        + " sessionTranscript.length=" + sessionTranscript.length
                        + " selected=" + describe(selected)
                        + " passcode=" + (passcode == null ? "null" : "len:" + passcode.length()));
                MdocDeviceResponse response = walletApi.createDeviceResponse(
                        hWalletToken, deviceRequest, sessionTranscript, selected, passcode);
                AppLog.d(TAG, "createDeviceResponse ok length="
                        + (response == null || response.getResponse() == null
                                ? -1 : response.getResponse().length)
                        + " authMethods="
                        + (response == null ? "null" : response.getAuthMethods()));
                return response;
            } catch (UtilityException | WalletCoreException | WalletException | RuntimeException e) {
                AppLog.e(TAG, "createDeviceResponse failed selected=" + describe(selected)
                        + " sessionTranscript.length=" + sessionTranscript.length
                        + " -> " + describe(e), e);
                throw rethrow(e);
            }
        });
    }

    @NonNull
    private static String describe(@NonNull List<MdocRequestedDocument> selected) {
        StringBuilder sb = new StringBuilder("[");
        for (MdocRequestedDocument doc : selected) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(doc.getDocRequestIndex()).append('/').append(doc.getDocType())
                    .append('/').append(doc.getCredentialId()).append(doc.getClaimCodes());
        }
        return sb.append(']').toString();
    }

    @NonNull
    private static String describe(@NonNull Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 512; t = t.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage());
            if (t.getCause() == t) break;
        }
        return sb.toString();
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
