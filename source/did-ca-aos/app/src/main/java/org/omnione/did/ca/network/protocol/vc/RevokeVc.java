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

package org.omnione.did.ca.network.protocol.vc;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import org.json.JSONException;
import org.json.JSONObject;
import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.protocol.P220RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P220ResponseVo;
import org.omnione.did.sdk.datamodel.security.ReqEcdh;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.common.enums.EllipticCurveType;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class RevokeVc {
    private static RevokeVc instance;
    private Context context;
    private String txId;
    private String hWalletToken;
    private String serverToken;
    private String ecdhResult;
	private String issuerNonce;
    private VerifyAuthType.VERIFY_AUTH_TYPE authType;
    private byte[] clientNonce;
    private EcKeyPair dhKeyPair;
    private String credentialId;
    public RevokeVc(){}
    public RevokeVc(Context context){
        this.context = context;
    }
    public static RevokeVc getInstance(Context context){
        if(instance == null) {
            instance = new RevokeVc(context);
        }
        return instance;
    }

    public CompletableFuture<VerifyAuthType.VERIFY_AUTH_TYPE> revokeVcPreProcess(String vcId) {
        String api1 = "/tas/api/v1/propose-revoke-vc";
        String api2 = "/tas/api/v1/request-ecdh";
        String api3 = "/tas/api/v1/request-create-token";
        String api5 = "/tas/api/v1/request-revoke-vc";
        String api6 = "/tas/api/v1/confirm-revoke-vc";

        String api_cas1 = "/cas/api/v1/request-wallet-tokendata";
        String api_cas2 = "/cas/api/v1/request-attested-appinfo";

        credentialId = vcId;
        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api1, "POST", M220_ProposeRevokeVc(vcId)))
                .thenCompose(_M220_ProposeRevokeVc -> {
                    txId = MessageUtil.deserialize(_M220_ProposeRevokeVc, P220ResponseVo.class).getTxId();
                    issuerNonce = MessageUtil.deserialize(_M220_ProposeRevokeVc, P220ResponseVo.class).getIssuerNonce();
                    authType = MessageUtil.deserialize(_M220_ProposeRevokeVc, P220ResponseVo.class).getAuthType();
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api2, "POST", M220_RequestEcdh(_M220_ProposeRevokeVc)));
                })
                .thenCompose(_M220_RequestEcdh -> {
                    ecdhResult = _M220_RequestEcdh;
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
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api3, "POST", M220_RequestCreateToken(serverTokenSeed)));
                })
                .thenApply(_M220_RequestCreateToken -> {
                    try {
                        serverToken = TokenUtil.createServerToken(_M220_RequestCreateToken, ecdhResult, clientNonce, dhKeyPair);
                    } catch (UtilityException e) {
                        throw new CompletionException(e);
                    }
                    return authType;
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    public CompletableFuture<String> revokeVcProcess(String pin) {
        String _M220_RequestRevokeVc = M220_RequestRevokeVc(credentialId, issuerNonce, pin);
        if(_M220_RequestRevokeVc == null){
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, "[Error] revoke VC failed");
            });
            return null;
        }
        deleteVc(credentialId);

        String api6 = "/tas/api/v1/confirm-revoke-vc";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();
        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api6, "POST", M220_ConfirmRevokeVc()))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }
    private String M220_ProposeRevokeVc(String vcId){
        P220RequestVo requestVo = new P220RequestVo(CaUtil.createMessageId(context));
        requestVo.setVcId(vcId);
        return requestVo.toJson();
    }

    private String M220_RequestEcdh(String result){
        P220RequestVo requestVo = new P220RequestVo(CaUtil.createMessageId(context), MessageUtil.deserialize(result, P220ResponseVo.class).getTxId());
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
            reqEcdh = (ReqEcdh) walletApi.addProofsToDocument(reqEcdh, List.of("keyagree"), did, Constants.DID_TYPE_HOLDER, "", false);
        } catch (WalletException | UtilityException | WalletCoreException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        requestVo.setReqEcdh(reqEcdh);
        return requestVo.toJson();
    }

    private String M220_RequestCreateToken(ServerTokenSeed serverTokenSeed){
        P220RequestVo requestVo = new P220RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setSeed(serverTokenSeed);
        return requestVo.toJson();
    }

    private String M220_RequestIssueProfile(String result){
        P220RequestVo requestVo = new P220RequestVo(CaUtil.createMessageId(context), txId);
        this.serverToken = result;
        requestVo.setServerToken(serverToken);
        return requestVo.toJson();
    }

    private String M220_RequestRevokeVc(String vcId, String issuerNonce, String pin){
        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    String result = walletApi.requestRevokeVc(hWalletToken, Config.TAS_URL, serverToken, txId, vcId, issuerNonce, pin, authType).get();
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

    private String M220_ConfirmRevokeVc(){
        P220RequestVo requestVo = new P220RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setServerToken(serverToken);
        return requestVo.toJson();
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        WalletTokenSeed walletTokenSeed = new WalletTokenSeed();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        } catch (WalletException | UtilityException | WalletCoreException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return walletTokenSeed.toJson();
    }

    private ServerTokenSeed createServerTokenSeed(String result) {
        ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
        serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.REVOKE_VC);
        SignedWalletInfo signedWalletInfo = new SignedWalletInfo();
        try{
            WalletApi walletApi = WalletApi.getInstance(context);
            String did = walletApi.getDIDDocument(1).getId();
            signedWalletInfo = walletApi.getSignedWalletInfo();
        } catch (WalletException | WalletCoreException | UtilityException e) {
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
        return createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.REMOVE_VC);
    }
    private String M000_GetAttestedAppInfo(String appId){
        JSONObject json = new JSONObject();
        try {
            json.put("appId", appId);
        } catch (JSONException e) {
            CaLog.e("deleteVc fail" + e.getMessage());
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
    public void deleteVc(String vcId) {
        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    walletApi.deleteCredentials(hWalletToken,vcId);
                } catch (WalletException | WalletCoreException | UtilityException e) {
                    CaLog.e("deleteVc fail" + e.getMessage());
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
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
    }

    public void authenticateBio(Fragment fragment, NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override
                public void onSuccess(String result) {
                    try {
                        revokeVcProcess("").get();
                    } catch (ExecutionException | InterruptedException e) {
                        CaLog.e("bio authentication fail" + e.getMessage());
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, e.getMessage());
                        });
                    }
                    navController.navigate(R.id.action_selectAuthTypetFragment_to_vcListFragment);
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
        } catch (WalletException | WalletCoreException e) {
            CaLog.e("bio authentication fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }

}
