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
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.protocol.P132ResponseVo;
import org.omnione.did.sdk.datamodel.protocol.P132RequestVo;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.common.enums.EllipticCurveType;
import org.omnione.did.sdk.datamodel.security.ReqEcdh;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.wallet.WalletApi;

import org.json.JSONObject;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class RegUser {
    private static RegUser instance;
    private Context context;
    private String txId;
    private String hWalletToken;
    private String serverToken;
    private String ecdhResult;
    private byte[] clientNonce;
    private EcKeyPair dhKeyPair;
    public RegUser(){}
    public RegUser(Context context){
        this.context = context;
    }
    public static RegUser getInstance(Context context) {
        if(instance == null) {
            instance = new RegUser(context);
        }
        return instance;
    }

    public CompletableFuture<String> regUserPreProcess() {
        String prefixTas = "/tas/api/v1";
        String prefixCas = "/cas/api/v1";

        String api1 = prefixTas + "/propose-register-user";
        String api2 = prefixTas + "/request-ecdh";
        String api3 = prefixTas + "/request-create-token";
        String api4 = prefixTas + "/retrieve-kyc";
        String api5 = prefixTas + "/request-register-user";

        String api_cas1 = prefixCas + "/request-wallet-tokendata";
        String api_cas2 = prefixCas + "/request-attested-appinfo";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api1, "POST", M132_ProposeRegisterUser()))
                .thenCompose(_M132_ProposeRegisterUser -> {
                    txId = MessageUtil.deserialize(_M132_ProposeRegisterUser, P132ResponseVo.class).getTxId();
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api2, "POST", M132_RequestEcdh()));
                })
                .thenCompose(_M132_RequestEcdh -> {
                    ecdhResult = _M132_RequestEcdh;
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
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api3, "POST", M132_RequestCreateToken(serverTokenSeed)));
                })
                .thenCompose(_M132_RequestCreateToken -> {
                    try {
                        serverToken = TokenUtil.createServerToken(_M132_RequestCreateToken, ecdhResult, clientNonce, dhKeyPair);
                    } catch (UtilityException e) {
                        throw new CompletionException(e);
                    }
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api4, "POST", M132_RetrieveKyc(serverToken)));
                })
                .thenApply(_M132_RetrieveKyc -> _M132_RetrieveKyc)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }
    public CompletableFuture<String> regUserProcess(SignedDidDoc signedDIDDoc) {
        String api6 = "/tas/api/v1/confirm-register-user";
        String _M132_RequestRegisterUser = M132_RequestRegisterUser(txId, serverToken, signedDIDDoc);

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api6, "POST", M132_ConfirmRegisterUser(_M132_RequestRegisterUser)))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });


    }

    private String M132_ProposeRegisterUser(){
        P132RequestVo requestVo = new P132RequestVo(CaUtil.createMessageId(context));
        String request = requestVo.toJson();
        return request;
    }

    private String M132_RequestEcdh(){
        P132RequestVo requestVo = new P132RequestVo(CaUtil.createMessageId(context), txId);
        ReqEcdh reqEcdh = new ReqEcdh();
        String did = "";
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            did = walletApi.getDIDDocument( Constants.DID_TYPE_DEVICE).getId();
            reqEcdh.setClient(did);
            clientNonce = CryptoUtils.generateNonce(16);
            dhKeyPair = CryptoUtils.generateECKeyPair(EcType.EC_TYPE.SECP256_R1);
            reqEcdh.setClientNonce(MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, clientNonce));
            reqEcdh.setCurve(EllipticCurveType.ELLIPTIC_CURVE_TYPE.SECP256R1);
            reqEcdh.setPublicKey(dhKeyPair.getPublicKey());
            reqEcdh.setCandidate(new ReqEcdh.Ciphers(List.of(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256CBC)));
            reqEcdh = (ReqEcdh) walletApi.addProofsToDocument(reqEcdh, List.of("keyagree"), did, Constants.DID_TYPE_DEVICE, null, false);
        } catch (WalletException | UtilityException | WalletCoreException e) {
            CaLog.e("ECDH failed : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        requestVo.setReqEcdh(reqEcdh);
        String request = requestVo.toJson();
        return request;
    }

    private String M132_RequestCreateToken(ServerTokenSeed serverTokenSeed){
        P132RequestVo requestVo = new P132RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setSeed(serverTokenSeed);
        String request = requestVo.toJson();
        return request;
    }

    private String M132_RetrieveKyc(String serverToken){
        P132RequestVo requestVo = new P132RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setServerToken(serverToken);
        requestVo.setKycTxId(Preference.getUserIdForDemo(context));
        String request = requestVo.toJson();
        return request;
    }

    private String M132_RequestRegisterUser(String txId, String serverToken, SignedDidDoc signedDIDDoc) {
        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    String result = walletApi.requestRegisterUser(hWalletToken, Config.TAS_URL, txId, serverToken, signedDIDDoc).get();
                    resultHolder[0] = result;
                } catch (WalletException | WalletCoreException e) {
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

    private String M132_ConfirmRegisterUser(String result){
        P132RequestVo requestVo = new P132RequestVo(CaUtil.createMessageId(context), MessageUtil.deserialize(result, P132ResponseVo.class).getTxId());
        requestVo.setServerToken(serverToken);
        String request = requestVo.toJson();
        return request;
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        WalletTokenSeed walletTokenSeed = new WalletTokenSeed();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        } catch (WalletCoreException | UtilityException | WalletException e){
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return walletTokenSeed.toJson();
    }
    private ServerTokenSeed createServerTokenSeed(String result) {
        ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
        serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.CREATE_DID);
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
        String request = createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.CREATE_DID);
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

    public void regUser(int type, String pin, Fragment fragment, NavController navController) throws WalletCoreException, UtilityException, WalletException {
        WalletApi walletApi = WalletApi.getInstance(context);
        if(type == Constants.PIN_TYPE_REG_KEY)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        walletApi.generateKeyPair(hWalletToken, pin);
                    } catch (Exception e) {
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, e.getMessage());
                        });
                    }
                }
            }).start();
        else if(type == Constants.PIN_TYPE_USE_KEY) {
            DIDDocument holderDIDDoc = walletApi.getDIDDocument(Constants.DID_TYPE_HOLDER);
            if(walletApi.isSavedKey(Constants.KEY_ID_BIO)){
                authenticateBio(holderDIDDoc, pin, fragment ,navController);
            } else {
                DIDDocument ownerDIDDoc = (DIDDocument) walletApi.addProofsToDocument(holderDIDDoc, List.of("pin"), holderDIDDoc.getId(), Constants.DID_TYPE_HOLDER, pin, false);
                registerUser(walletApi.createSignedDIDDoc(ownerDIDDoc));
                Preference.setInit(context, true);
                navController.navigate(R.id.action_stepFragment_to_vcListFragment);
            }
        }
    }

    private void authenticateBio(DIDDocument holderDIDDoc, String pin, Fragment fragment, NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override
                public void onSuccess(String result) {
                    CaLog.d("RegUser authenticateBioKey onSuccess");
                    try {
                        DIDDocument ownerDIDDoc = (DIDDocument) walletApi.addProofsToDocument(holderDIDDoc, List.of("pin", "bio"), holderDIDDoc.getId(), Constants.DID_TYPE_HOLDER, pin, false);
                        registerUser(walletApi.createSignedDIDDoc(ownerDIDDoc));
                        Preference.setInit(context, true);
                        navController.navigate(R.id.action_stepFragment_to_vcListFragment);
                    } catch (Exception e){
                        CaLog.e("bio key signing fail" + e.getMessage());
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
                    CaLog.e("registerBioKey onFail : " + result);
                }
            });
            walletApi.authenticateBioKey(fragment, context);
        } catch (Exception e) {
            CaLog.e("bio authentication fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }
    // create Holder didDoc
    public void createHolderDocByPin(NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletApi.createHolderDIDDoc(hWalletToken);
            ContextCompat.getMainExecutor(context).execute(()  -> {
                Bundle bundle = new Bundle();
                bundle.putInt("step", Constants.STEP3);
                navController.navigate(R.id.action_stepFragment_self, bundle);
            });
        } catch (Exception e) {
            CaLog.e("PIN key creation fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }
    public void createHolderDocByBio(NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override
                public void onSuccess(String result) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                walletApi.createHolderDIDDoc(hWalletToken);
                                ContextCompat.getMainExecutor(context).execute(()  -> {
                                    Bundle bundle = new Bundle();
                                    bundle.putInt("step", Constants.STEP3);
                                    navController.navigate(R.id.action_stepFragment_self, bundle);
                                });
                            } catch (Exception e) {
                                CaLog.e("bio key creation fail " + e.getMessage());
                                ContextCompat.getMainExecutor(context).execute(()  -> {
                                    CaUtil.showErrorDialog(context, e.getMessage());
                                });
                            }
                        }
                    }).start();
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
                    CaLog.e("RegUser registerBioKey onFail : " + result);
                }
            });
            walletApi.registerBioKey(context);
        } catch (Exception e) {
            CaLog.e("bio key creation fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }

    private void registerUser(SignedDidDoc signedDIDDoc){
        try {
            regUserProcess(signedDIDDoc).get();
        } catch (Exception e) {
            CaLog.e("registerUser error : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            Preference.setDID(context, walletApi.getDIDDocument( Constants.DID_TYPE_HOLDER).getId());
        } catch (Exception e){
            CaLog.e("did save error : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        // update push token (optional)
        CaUtil.updatePushToken(context);
    }
}
