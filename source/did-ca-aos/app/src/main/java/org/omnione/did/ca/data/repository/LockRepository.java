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

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.RequestWalletTokenDataOp;
import org.omnione.did.ca.data.network.protocol.SignupOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class LockRepository {

    private final Context appCtx;
    private final WalletGateway gateway;
    private final NetworkManager net;
    private final AppConfig config;
    private final RegistrationSession session;
    private final UserIdentityRepository identity;
    private final Executor executor = Executors.newCachedThreadPool();

    @Inject
    public LockRepository(@ApplicationContext Context appCtx,
                                 WalletGateway gateway,
                                 NetworkManager net,
                                 AppConfig config,
                                 RegistrationSession session,
                                 UserIdentityRepository identity) {
        this.appCtx = appCtx;
        this.gateway = gateway;
        this.net = net;
        this.config = config;
        this.session = session;
        this.identity = identity;
    }

        public CompletableFuture<Void> registerUnlockPin(String pin) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String tas = config.tasUrl();
                String cas = config.casUrl();

                String userId = identity.getUserId();
                String caAppId = identity.getOrCreateCaAppId();
                if (userId == null || userId.isEmpty()) {
                    userId = java.util.UUID.randomUUID().toString();
                    SignupOp.call(net, cas, userId, caAppId);
                    identity.setUserId(userId);
                }

                WalletTokenSeed seed = gateway.createWalletTokenSeed(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE_AND_CONFIGLOCK,
                        CaUtil.getPackageName(appCtx),
                        userId).get();
                String tokenData = RequestWalletTokenDataOp.call(net, cas, seed);
                String hWalletToken = TokenUtil.createHashWalletToken(
                        tokenData, appCtx, config.apiGwUrl());

                gateway.bindUser(hWalletToken).get();
                gateway.registerLock(hWalletToken, pin, true).get();

                session.markPersonalizeDone();
                return null;
            } catch (Throwable t) {
                throw new CompletionException(t);
            }
        }, executor);
    }

        public CompletableFuture<Void> registerSigningPin(String pin) {

        return session.awaitHWalletToken()
                .thenCompose(walletToken ->
                        gateway.deleteKey(walletToken, Arrays.asList(Constants.KEY_ID_PIN, Constants.KEY_ID_KEY_AGREE))
                                .exceptionally(t -> null)
                                .thenCompose(v -> gateway.generateKeyPair(walletToken, pin)));
    }

        public CompletableFuture<Void> authenticateUnlock(String pin) {
        return gateway.authenticateLock(pin);
    }

        public CompletableFuture<Void> authenticateSigning(String pin) {
        return gateway.authenticatePin(Constants.KEY_ID_PIN, pin);
    }

        public CompletableFuture<Void> changeUnlockPin(String oldPin, String newPin) {
        return gateway.changeLock(oldPin, newPin);
    }

        public CompletableFuture<Void> changeSigningPin(String oldPin, String newPin) {
        return gateway.changePin(Constants.KEY_ID_PIN, oldPin, newPin);
    }
}
