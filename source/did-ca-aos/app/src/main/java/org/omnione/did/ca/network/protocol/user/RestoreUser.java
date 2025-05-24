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

package org.omnione.did.ca.network.protocol.user;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import org.json.JSONObject;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.common.enums.EllipticCurveType;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.protocol.P142RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P142ResponseVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.security.ReqEcdh;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.WalletApi;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class RestoreUser {
    private static RestoreUser instance;
    private Context context;
    private String txId;
    private String authNonce;
    private String hWalletToken;
    private String serverToken;
    private String ecdhResult;
    private byte[] clientNonce;
    private EcKeyPair dhKeyPair;

    public RestoreUser(){}
    public RestoreUser(Context context){
        this.context = context;
    }
    public static RestoreUser getInstance(Context context){
        if(instance == null) {
            instance = new RestoreUser(context);
        }
        return instance;
    }

    public CompletableFuture<String> restoreUserPreProcess(String did, String offerId) {
        String api1 = "/tas/api/v1/propose-restore-diddoc";
        String api2 = "/tas/api/v1/request-ecdh";
        String api3 = "/tas/api/v1/request-create-token";
        String api4 = "/tas/api/v1/request-restore-diddoc";
        String api6 = "/tas/api/v1/confirm-restore-diddoc";

        String api_cas1 = "/cas/api/v1/request-wallet-tokendata";
        String api_cas2 = "/cas/api/v1/request-attested-appinfo";


        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api1, "POST", M142_ProposeRestoreDidDoc(did, offerId)))
                .thenCompose(_M142_ProposeRestoreDidDoc -> {
                    txId = MessageUtil.deserialize(_M142_ProposeRestoreDidDoc, P142ResponseVo.class).getTxId();
                    authNonce = txId = MessageUtil.deserialize(_M142_ProposeRestoreDidDoc, P142ResponseVo.class).getAuthNonce();
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api2, "POST", M142_RequestEcdh(_M142_ProposeRestoreDidDoc)));
                })
                .thenCompose(_M142_RequestEcdh -> {
                    ecdhResult = _M142_RequestEcdh;
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.CAS_URL + api_cas1, "POST", M000_GetWalletTokenData()));
                })
                .thenCompose(_M000_GetWalletTokenData -> {
                    try {
                        hWalletToken = TokenUtil.createHashWalletToken(_M000_GetWalletTokenData, context);
                    } catch (WalletException | WalletCoreException | UtilityException |
                             ExecutionException | InterruptedException e) {
                        throw new CompletionException(e);
                    }
                    String appId = Preference.getCaAppId(context);
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.CAS_URL + api_cas2, "POST", M000_GetAttestedAppInfo(appId)));
                })
                .thenCompose(_M000_GetAttestedAppInfo -> {
                    ServerTokenSeed serverTokenSeed = createServerTokenSeed(_M000_GetAttestedAppInfo);
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api3, "POST", M142_RequestCreateToken(serverTokenSeed)));
                })
                .thenApply(_M142_RequestCreateToken -> {
                    try {
                        serverToken = TokenUtil.createServerToken(_M142_RequestCreateToken, ecdhResult, clientNonce, dhKeyPair);
                    } catch (UtilityException e) {
                        throw new CompletionException(e);
                    }
                    return serverToken;
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    public CompletableFuture<String> restoreUserProcess(DIDAuth signedDIDAuth) {
        String _M142_RequestRestoreDidDoc = M142_RequestRestoreDidDoc(txId, serverToken, signedDIDAuth);
        String api6 = "/tas/api/v1/confirm-restore-diddoc";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api6, "POST", M142_ConfirmRestoreDidDoc()))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    private String M142_ProposeRestoreDidDoc(String did, String offerId){
        P142RequestVo requestVo = new P142RequestVo(CaUtil.createMessageId(context));
        requestVo.setDid(did);
        requestVo.setOfferId(offerId);
        String request = requestVo.toJson();
        return request;
    }

    private String M142_RequestEcdh(String result){
        P142RequestVo requestVo = new P142RequestVo(CaUtil.createMessageId(context), MessageUtil.deserialize(result, P142ResponseVo.class).getTxId());

        ReqEcdh reqEcdh = new ReqEcdh();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            String did = walletApi.getDIDDocument(Constants.DID_TYPE_HOLDER).getId();
            reqEcdh.setClient(did);
            clientNonce = CryptoUtils.generateNonce(16);
            dhKeyPair = CryptoUtils.generateECKeyPair(EcType.EC_TYPE.SECP256_R1);
            reqEcdh.setClientNonce(MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, clientNonce));
            reqEcdh.setCurve(EllipticCurveType.ELLIPTIC_CURVE_TYPE.SECP256R1);
            reqEcdh.setPublicKey(dhKeyPair.getPublicKey());
            reqEcdh.setCandidate(new ReqEcdh.Ciphers(List.of(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256CBC)));
            reqEcdh = (ReqEcdh) walletApi.addProofsToDocument(reqEcdh, List.of("keyagree"), did, Constants.DID_TYPE_HOLDER, null, false);
        } catch (Exception e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        requestVo.setReqEcdh(reqEcdh);
        String request = requestVo.toJson();
        return request;
    }

    private String M142_RequestCreateToken(ServerTokenSeed serverTokenSeed){
        P142RequestVo requestVo = new P142RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setSeed(serverTokenSeed);
        String request = requestVo.toJson();
        return request;
    }

    private String M142_RequestRestoreDidDoc(String txId, String serverToken, DIDAuth signedDIDAuth){

        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    String result = walletApi.requestRestoreUser(hWalletToken, Config.TAS_URL, serverToken, signedDIDAuth, txId).get();
                    resultHolder[0] = result;
                } catch (WalletException | UtilityException | WalletCoreException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                } catch (ExecutionException | InterruptedException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, cause.getCause().getMessage());
                        });
                    }
                } finally {
                    latch.countDown();
                }
            }
        }).start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return resultHolder[0];
    }

    private String M142_ConfirmRestoreDidDoc(){
        P142RequestVo requestVo = new P142RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setServerToken(serverToken);
        String request = requestVo.toJson();
        return request;
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        WalletTokenSeed walletTokenSeed = new WalletTokenSeed();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        } catch (Exception e){
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return walletTokenSeed.toJson();
    }

    private ServerTokenSeed createServerTokenSeed(String result) {
        ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
        serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.RESTORE_DID);
        SignedWalletInfo signedWalletInfo = new SignedWalletInfo();
        try{
            WalletApi walletApi = WalletApi.getInstance(context);
            String did = walletApi.getDIDDocument(1).getId();
            signedWalletInfo = walletApi.getSignedWalletInfo();
        } catch (Exception e){
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        serverTokenSeed.setWalletInfo(signedWalletInfo);
        AttestedAppInfo attestedAppInfo = MessageUtil.deserialize(result, AttestedAppInfo.class);
        serverTokenSeed.setCaAppInfo(attestedAppInfo);

        return serverTokenSeed;

    }

    private String M000_GetWalletTokenData() {
        String request = createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.RESTORE_DID);
        return request;
    }
    private String M000_GetAttestedAppInfo(String appId){
        JSONObject json = new JSONObject();
        try {
            json.put("appId", appId);
        } catch (Exception e){
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return json.toString();
    }

    public boolean isBioKey() throws WalletCoreException, UtilityException, WalletException {
        WalletApi walletApi = WalletApi.getInstance(context);
        return walletApi.isSavedKey(Constants.KEY_ID_BIO);
    }
    public void getSignedDIDAuthByPin(String authNonce, String pin, NavController navController) throws WalletException, WalletCoreException, UtilityException {
        WalletApi walletApi = WalletApi.getInstance(context);
        DIDAuth signedDIDAuth = walletApi.getSignedDIDAuth(authNonce, pin);
        restoreUser(signedDIDAuth, navController);
    }

    public void authenticateBio(String authNonce, Fragment fragment, NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            DIDAuth didAuth = walletApi.getSignedDIDAuth(authNonce, null);
            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override
                public void onSuccess(String result) {
                    try {
                        DIDDocument holderDIDDoc = walletApi.getDIDDocument(Constants.DID_TYPE_HOLDER);
                        DIDAuth signedDIDAuth = (DIDAuth) walletApi.addProofsToDocument(didAuth, List.of("bio"), holderDIDDoc.getId(), Constants.DID_TYPE_HOLDER, null, true);
                        restoreUser(signedDIDAuth, navController);
                    } catch (Exception e){
                        CaLog.e("bio key authentication fail" + e.getMessage());
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, e.getMessage());
                        });
                    }
                }
                @Override
                public void onError(String result) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context,"[Error] Authentication failed.\nPlease try again later.");
                    });
                }
                @Override
                public void onCancel(String result) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context,"[Information] canceled by user");
                    });
                }
                @Override
                public void onFail(String result) {
                    CaLog.e("bio onFail : " + result);
                }
            });
            walletApi.authenticateBioKey(fragment, context);
        } catch (WalletException | WalletCoreException | UtilityException e) {
            CaLog.e("bio key authentication fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }

    private void restoreUser(DIDAuth signedDIDAuth, NavController navController){
        try {
            restoreUserProcess(signedDIDAuth).get();
        } catch (Exception e) {
            CaLog.e("restoreUser error : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        //Bundle bundle = new Bundle();
        //bundle.putString("type",Constants.TYPE_ISSUE);
        //navController.navigate(R.id.action_profileFragment_to_resultFragment, bundle);
    }
}
