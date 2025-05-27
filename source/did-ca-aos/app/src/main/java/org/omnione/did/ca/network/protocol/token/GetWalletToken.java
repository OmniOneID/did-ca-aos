/*
 * Copyright 2024 OmniOne.
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

package org.omnione.did.ca.network.protocol.token;

import android.content.Context;
import android.os.Build;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.utility.Errors.UtilityException;

import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import org.omnione.did.sdk.wallet.walletservice.network.HttpUrlConnection;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class GetWalletToken {
    private static GetWalletToken instance;
    private Context context;
    public GetWalletToken(){}
    public GetWalletToken(Context context){
        this.context = context;
    }
    public static GetWalletToken getInstance(Context context) {
        if(instance == null) {
            instance = new GetWalletToken(context);
        }
        return instance;
    }
    public CompletableFuture<String> getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        String api1 = "/cas/api/v1/request-wallet-tokendata";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return httpUrlConnection.send(Config.CAS_URL + api1, "POST", M000_GetWalletTokenData(purpose));
                    } catch (WalletCoreException | UtilityException | CommunicationException |
                             WalletException e) {
                        throw new CompletionException(e);
                    }
                })
                .thenCompose(_M000_GetWalletTokenData -> {
                    if (_M000_GetWalletTokenData == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            return CompletableFuture.failedFuture(new RuntimeException("Failed at M000_GetWalletTokenData"));
                        }
                    }
                    String walletToken = null;
                    try {
                        walletToken = TokenUtil.createHashWalletToken(_M000_GetWalletTokenData, context);
                    } catch (WalletException | WalletCoreException | UtilityException |
                             ExecutionException | InterruptedException e) {
                        throw new CompletionException(e);
                    }
                    return CompletableFuture.completedFuture(walletToken);
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    private String M000_GetWalletTokenData(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) throws UtilityException, WalletCoreException, WalletException {
        String request = createWalletTokenSeed(purpose);
        return request;
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) throws UtilityException, WalletCoreException, WalletException {
        WalletApi walletApi = WalletApi.getInstance(context);
        WalletTokenSeed walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        return  walletTokenSeed.toJson();
    }
}
