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

package org.omnione.did.ca.data.network.protocol;

import android.content.Context;

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class GetWalletTokenOp {

    private GetWalletTokenOp() {}

    public static CompletableFuture<String> call(WalletGateway gateway,
                                                 NetworkManager net,
                                                 AppConfig config,
                                                 Context ctx,
                                                 String userId,
                                                 WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        String pkgName = CaUtil.getPackageName(ctx);
        return gateway.createWalletTokenSeed(purpose, pkgName, userId)
                .thenCompose(seed -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String tokenData = RequestWalletTokenDataOp.call(net, config.casUrl(), seed);
                        return TokenUtil.createHashWalletToken(tokenData, ctx, config.apiGwUrl());
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }));
    }
}
