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
import org.omnione.did.ca.data.network.protocol.ConfirmRegisterUserOp;
import org.omnione.did.ca.data.network.protocol.ProposeRegisterUserOp;
import org.omnione.did.ca.data.network.protocol.RequestAttestedAppInfoOp;
import org.omnione.did.ca.data.network.protocol.RequestCreateTokenOp;
import org.omnione.did.ca.data.network.protocol.RequestEcdhOp;
import org.omnione.did.ca.data.network.protocol.RequestWalletTokenDataOp;
import org.omnione.did.ca.data.network.protocol.RetrieveKycOp;
import org.omnione.did.ca.data.network.protocol.SignupOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.protocol.P132ResponseVo;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class UserRegistrationRepository {

    private static final String TAG = "RegUser";

    private final Context appCtx;
    private final WalletGateway gateway;
    private final NetworkManager net;
    private final AppConfig config;
    private final RegistrationSession session;
    private final UserIdentityRepository identity;
    private final Executor executor = Executors.newCachedThreadPool();

    @Inject
    public UserRegistrationRepository(@ApplicationContext Context ctx,
                                             WalletGateway gateway,
                                             NetworkManager net,
                                             AppConfig config,
                                             RegistrationSession session,
                                             UserIdentityRepository identity) {
        this.appCtx = ctx;
        this.gateway = gateway;
        this.net = net;
        this.config = config;
        this.session = session;
        this.identity = identity;
    }

        public CompletableFuture<Void> preExecute() {
        return CompletableFuture.runAsync(() -> {
            try {
                String tas = config.tasUrl();
                String cas = config.casUrl();
                WalletApi walletApi = WalletApi.getInstance(appCtx);

                String userId = identity.getUserId();
                String caAppId = identity.getOrCreateCaAppId();
                boolean freshUser = (userId == null || userId.isEmpty());
                if (freshUser) {
                    userId = java.util.UUID.randomUUID().toString();
                    SignupOp.call(net, cas, userId, caAppId);
                    identity.setUserId(userId);
                }

                if (!session.isPersonalizeDone()) {
                    WalletTokenSeed personalizeSeed = gateway.createWalletTokenSeed(
                            WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE,
                            CaUtil.getPackageName(appCtx),
                            userId).get();
                    String personalizeTokenData = RequestWalletTokenDataOp.call(net, cas, personalizeSeed);
                    String personalizeHWalletToken = TokenUtil.createHashWalletToken(
                            personalizeTokenData, appCtx, config.apiGwUrl());
                    gateway.bindUser(personalizeHWalletToken).get();
                }

                String proposeRes = ProposeRegisterUserOp.call(net, appCtx, tas);
                String txId = MessageUtil.deserialize(proposeRes, P132ResponseVo.class).getTxId();

                RequestEcdhOp.Result ecdh = RequestEcdhOp.call(net, appCtx, tas, walletApi, txId);
                String ecdhResult = ecdh.rawResponseJson;
                byte[] clientNonce = ecdh.clientNonce;
                org.omnione.did.sdk.utility.DataModels.EcKeyPair dhKeyPair = ecdh.dhKeyPair;

                WalletTokenSeed walletTokenSeed = gateway.createWalletTokenSeed(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.CREATE_DID,
                        CaUtil.getPackageName(appCtx),
                        identity.getUserId()).get();
                String walletTokenData = RequestWalletTokenDataOp.call(net, cas, walletTokenSeed);
                String hWalletToken = TokenUtil.createHashWalletToken(
                        walletTokenData, appCtx, config.apiGwUrl());

                String attestedAppInfoJson = RequestAttestedAppInfoOp.call(
                        net, cas, identity.getOrCreateCaAppId());

                ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
                serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.CREATE_DID);
                gateway.getDIDDocument(1).get();
                SignedWalletInfo signedWalletInfo = gateway.getSignedWalletInfo().get();
                serverTokenSeed.setWalletInfo(signedWalletInfo);
                AttestedAppInfo attestedAppInfo = MessageUtil.deserialize(
                        attestedAppInfoJson, AttestedAppInfo.class);
                serverTokenSeed.setCaAppInfo(attestedAppInfo);

                String createTokenResult = RequestCreateTokenOp.call(
                        net, appCtx, tas, txId, serverTokenSeed);
                String serverToken = TokenUtil.createServerToken(
                        createTokenResult, ecdhResult, clientNonce, dhKeyPair);

                RetrieveKycOp.call(
                        net, appCtx, tas, txId, serverToken, identity.getUserId());

                session.setPreExecuteResult(txId, hWalletToken, serverToken);
            } catch (Throwable t) {
                AppLog.e(TAG, "preExecute failed", t);
                session.failPreExecute(t);
                throw new CompletionException(t);
            }
        }, executor);
    }

        public CompletableFuture<Void> registerUser(boolean viaBiometric, String pin) {
        return session.awaitHWalletToken().thenComposeAsync(hWalletToken ->
                CompletableFuture.supplyAsync(() -> {
                    try {

                        DIDDocument unsigned = gateway.createHolderDIDDoc(hWalletToken).get();

                        java.util.List<String> keyIds = viaBiometric
                                ? Arrays.asList(Constants.KEY_ID_PIN, Constants.KEY_ID_BIO)
                                : Collections.singletonList(Constants.KEY_ID_PIN);

                        DIDDocument ownerDoc = (DIDDocument) gateway.addProofsToDocument(
                                unsigned, keyIds, unsigned.getId(),
                                Constants.DID_DOC_TYPE_HOLDER, pin, false).get();

                        SignedDidDoc signedDoc = gateway.createSignedDIDDoc(ownerDoc).get();

                        String registerResult = gateway.requestRegisterUser(
                                hWalletToken, config.tasUrl(),
                                session.getTxId(), session.getServerToken(), signedDoc).get();

                        String confirmTxId = MessageUtil.deserialize(
                                registerResult, P132ResponseVo.class).getTxId();
                        ConfirmRegisterUserOp.call(net, appCtx, config.tasUrl(),
                                confirmTxId, session.getServerToken());

                        gateway.saveDocument().get();

                        String did = gateway.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).get().getId();

                        identity.setDid(did);
                        identity.setInitialized(true);
                        session.clear();
                        return null;
                    } catch (Throwable t) {
                        AppLog.e(TAG, "registerUser failed", t);
                        throw new CompletionException(t);
                    }
                }, executor), executor);
    }
}
