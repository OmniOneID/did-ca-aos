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
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.ConfirmUpdateDidDocOp;
import org.omnione.did.ca.data.network.protocol.ProposeUpdateDidDocOp;
import org.omnione.did.ca.data.network.protocol.RequestAttestedAppInfoOp;
import org.omnione.did.ca.data.network.protocol.RequestCreateTokenOp;
import org.omnione.did.ca.data.network.protocol.RequestEcdhOp;
import org.omnione.did.ca.data.network.protocol.RequestWalletTokenDataOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.protocol.P141ResponseVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
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
public class BiometricRepository {

    private static final String TAG = "BioRepo";

    private final Context appCtx;
    private final WalletGateway gateway;
    private final RegistrationSession session;
    private final NetworkManager net;
    private final AppConfig config;
    private final UserIdentityRepository identity;
    private final Executor executor = Executors.newCachedThreadPool();

    @Inject
    public BiometricRepository(@ApplicationContext Context appCtx,
                                      WalletGateway gateway,
                                      RegistrationSession session,
                                      NetworkManager net,
                                      AppConfig config,
                                      UserIdentityRepository identity) {
        this.appCtx = appCtx;
        this.gateway = gateway;
        this.session = session;
        this.net = net;
        this.config = config;
        this.identity = identity;
    }

        public CompletableFuture<BioResult> registerBiometric(Context ctx) {

        String token = session.getHWalletTokenIfReady();
        CompletableFuture<Void> pre = (token != null)
                ? gateway.deleteKey(token, Collections.singletonList(Constants.KEY_ID_BIO))
                        .exceptionally(t -> null)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<BioResult> future = new CompletableFuture<>();
        pre.whenComplete((v, ignored) -> {
            gateway.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override public void onSuccess(String r) { future.complete(new BioResult(BioResult.Status.SUCCESS, r)); }
                @Override public void onError(String r)   { future.complete(new BioResult(BioResult.Status.ERROR,   r)); }
                @Override public void onCancel(String r)  { future.complete(new BioResult(BioResult.Status.CANCEL,  r)); }
                @Override public void onFail(String r)    { future.complete(new BioResult(BioResult.Status.FAIL,    r)); }
            });
            gateway.registerBioKey(ctx);
        });
        return future;
    }

        public CompletableFuture<BioResult> authenticateBiometric(Context ctx) {
        CompletableFuture<BioResult> future = new CompletableFuture<>();
        gateway.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
            @Override public void onSuccess(String r) { future.complete(new BioResult(BioResult.Status.SUCCESS, r)); }
            @Override public void onError(String r)   { future.complete(new BioResult(BioResult.Status.ERROR,   r)); }
            @Override public void onCancel(String r)  { future.complete(new BioResult(BioResult.Status.CANCEL,  r)); }
            @Override public void onFail(String r)    { future.complete(new BioResult(BioResult.Status.FAIL,    r)); }
        });
        gateway.authenticateBioKey(ctx);
        return future;
    }

        public CompletableFuture<Boolean> isBiometricRegistered() {
        return gateway.isSavedKey(Constants.KEY_ID_BIO);
    }

        public CompletableFuture<BioResult> registerBiometricFromSettings(Context activityCtx, String pin) {
        return preExecuteUpdate()
                .thenComposeAsync(ctx -> registerBioKeyOnly(activityCtx).thenApply(bio -> {
                    if (bio.status != BioResult.Status.SUCCESS) {
                        throw new CompletionException(new BioFlowAbort(bio));
                    }
                    return ctx;
                }), executor)
                .thenApplyAsync(ctx -> {
                    try {
                        commitUpdate(ctx, pin);
                        return new BioResult(BioResult.Status.SUCCESS, "");
                    } catch (Throwable t) {
                        throw new CompletionException(t);
                    }
                }, executor)
                .exceptionally(t -> {
                    Throwable cause = (t instanceof CompletionException && t.getCause() != null)
                            ? t.getCause() : t;
                    if (cause instanceof BioFlowAbort) {
                        return ((BioFlowAbort) cause).bio;
                    }
                    AppLog.e(TAG, "registerBiometricFromSettings failed", cause);
                    return new BioResult(BioResult.Status.ERROR, String.valueOf(cause.getMessage()));
                });
    }

    private static final class UpdateCtx {
        final String txId;
        final String authNonce;
        final String hWalletToken;
        final String serverToken;
        UpdateCtx(String txId, String authNonce, String hWalletToken, String serverToken) {
            this.txId = txId;
            this.authNonce = authNonce;
            this.hWalletToken = hWalletToken;
            this.serverToken = serverToken;
        }
    }

    private static final class BioFlowAbort extends RuntimeException {
        final BioResult bio;
        BioFlowAbort(BioResult bio) { super("bio aborted: " + bio.status); this.bio = bio; }
    }

    private CompletableFuture<UpdateCtx> preExecuteUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tas = config.tasUrl();
                String cas = config.casUrl();
                WalletApi walletApi = WalletApi.getInstance(appCtx);
                String holderDid = walletApi.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).getId();

                String proposeRes = ProposeUpdateDidDocOp.call(net, appCtx, tas, holderDid);
                P141ResponseVo proposeVo = MessageUtil.deserialize(proposeRes, P141ResponseVo.class);
                String txId = proposeVo.getTxId();
                String authNonce = proposeVo.getAuthNonce();

                RequestEcdhOp.Result ecdh = RequestEcdhOp.call(net, appCtx, tas, walletApi, txId);

                String attestedAppInfoJson = RequestAttestedAppInfoOp.call(
                        net, cas, identity.getOrCreateCaAppId());

                WalletTokenSeed walletTokenSeed = gateway.createWalletTokenSeed(
                        WalletTokenPurpose.WALLET_TOKEN_PURPOSE.UPDATE_DID,
                        CaUtil.getPackageName(appCtx),
                        identity.getUserId()).get();
                String walletTokenData = RequestWalletTokenDataOp.call(net, cas, walletTokenSeed);
                String hWalletToken = TokenUtil.createHashWalletToken(
                        walletTokenData, appCtx, config.apiGwUrl());

                ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
                serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.UPDATE_DID);
                SignedWalletInfo signedWalletInfo = gateway.getSignedWalletInfo().get();
                serverTokenSeed.setWalletInfo(signedWalletInfo);
                AttestedAppInfo attestedAppInfo = MessageUtil.deserialize(
                        attestedAppInfoJson, AttestedAppInfo.class);
                serverTokenSeed.setCaAppInfo(attestedAppInfo);

                String createTokenResult = RequestCreateTokenOp.call(
                        net, appCtx, tas, txId, serverTokenSeed);
                String serverToken = TokenUtil.createServerToken(
                        createTokenResult, ecdh.rawResponseJson, ecdh.clientNonce, ecdh.dhKeyPair);

                return new UpdateCtx(txId, authNonce, hWalletToken, serverToken);
            } catch (Throwable t) {
                AppLog.e(TAG, "preExecuteUpdate failed", t);
                throw new CompletionException(t);
            }
        }, executor);
    }

    private CompletableFuture<BioResult> registerBioKeyOnly(Context ctx) {
        CompletableFuture<BioResult> future = new CompletableFuture<>();
        gateway.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
            @Override public void onSuccess(String r) { future.complete(new BioResult(BioResult.Status.SUCCESS, r)); }
            @Override public void onError(String r)   { future.complete(new BioResult(BioResult.Status.ERROR,   r)); }
            @Override public void onCancel(String r)  { future.complete(new BioResult(BioResult.Status.CANCEL,  r)); }
            @Override public void onFail(String r)    { future.complete(new BioResult(BioResult.Status.FAIL,    r)); }
        });
        gateway.registerBioKey(ctx);
        return future;
    }

    private void commitUpdate(UpdateCtx ctx, String pin) throws Exception {
        DIDDocument unsigned = gateway.updateHolderDIDDoc(ctx.hWalletToken).get();
        DIDDocument ownerDoc = (DIDDocument) gateway.addProofsToDocument(
                unsigned, Arrays.asList(Constants.KEY_ID_PIN, Constants.KEY_ID_BIO), unsigned.getId(),
                Constants.DID_DOC_TYPE_HOLDER, pin, false).get();
        SignedDidDoc signedDoc = gateway.createSignedDIDDoc(ownerDoc).get();
        DIDAuth didAuth = gateway.getSignedDIDAuth(ctx.authNonce, pin).get();

        gateway.requestUpdateUser(
                ctx.hWalletToken, config.tasUrl(), ctx.serverToken, didAuth, signedDoc, ctx.txId).get();
        ConfirmUpdateDidDocOp.call(net, appCtx, config.tasUrl(), ctx.txId, ctx.serverToken);

        gateway.saveDocument().get();
    }
}
