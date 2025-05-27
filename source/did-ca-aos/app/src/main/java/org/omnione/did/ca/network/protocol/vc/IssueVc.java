/*
 * Copyright 2024-2025 OmniOne.
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
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import org.json.JSONException;
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
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.protocol.P210RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.common.enums.EllipticCurveType;
import org.omnione.did.sdk.datamodel.security.ReqEcdh;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.token.AttestedAppInfo;
import org.omnione.did.sdk.datamodel.common.enums.ServerTokenPurpose;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;

import org.json.JSONObject;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletErrorCode;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class IssueVc {
    private static IssueVc instance;
    private Context context;
    private String txId;
    private String hWalletToken;
    private String serverToken;
    private String ecdhResult;
	private String refId;
    private byte[] clientNonce;
    private EcKeyPair dhKeyPair;
    private String profile;
    public IssueVc(){}
    public IssueVc(Context context){
        this.context = context;
    }
    public static IssueVc getInstance(Context context){
        if(instance == null) {
            instance = new IssueVc(context);
        }
        return instance;
    }

    public CompletableFuture<String> issueVcPreProcess(String vcPlanId, String did, String offerId) {
        String api1 = "/tas/api/v1/propose-issue-vc";
        String api2 = "/tas/api/v1/request-ecdh";
        String api3 = "/tas/api/v1/request-create-token";
        String api4 = "/tas/api/v1/request-issue-profile";
        String api5 = "/tas/api/v1/request-issue-vc";
        String api6 = "/tas/api/v1/confirm-issue-vc";

        String api_cas1 = "/cas/api/v1/request-wallet-tokendata";
        String api_cas2 = "/cas/api/v1/request-attested-appinfo";


        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api1, "POST", M210_ProposeIssueVc(vcPlanId, did, offerId)))
                .thenCompose(_M210_ProposeIssueVc -> {
                    txId = MessageUtil.deserialize(_M210_ProposeIssueVc, P210ResponseVo.class).getTxId();
                    refId = MessageUtil.deserialize(_M210_ProposeIssueVc, P210ResponseVo.class).getRefId();
                    return CompletableFuture.supplyAsync(() -> {
                        return httpUrlConnection.send(context, Config.TAS_URL + api2, "POST", M210_RequestEcdh(_M210_ProposeIssueVc));
                    });
                })
                .thenCompose(_M210_RequestEcdh -> {
                    ecdhResult = _M210_RequestEcdh;
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
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api3, "POST", M210_RequestCreateToken(serverTokenSeed)));
                })
                .thenCompose(_M210_RequestCreateToken -> {
                    try {
                        serverToken = TokenUtil.createServerToken(_M210_RequestCreateToken, ecdhResult, clientNonce, dhKeyPair);
                    } catch (UtilityException e) {
                        throw new CompletionException(e);
                    }
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api4, "POST", M210_RequestIssueProfile(serverToken)));
                })
                .thenApply(_M210_RequestIssueProfile -> {
                    profile = _M210_RequestIssueProfile;
                    return _M210_RequestIssueProfile;
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    public CompletableFuture<String> issueVcProcess(IssueProfile profile, DIDAuth signedDIDAuth) throws WalletException {
        String _M210_RequestIssueVc = M210_RequestIssueVc(txId, serverToken, refId, profile, signedDIDAuth);

        if(_M210_RequestIssueVc.isEmpty())
            throw new WalletException(WalletErrorCode.ERR_CODE_WALLET_ISSUE_CREDENTIAL_FAIL);
        String api6 = "/tas/api/v1/confirm-issue-vc"; //VC 발급 완료

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context,Config.TAS_URL + api6, "POST", M210_ConfirmIssueVc(_M210_RequestIssueVc)))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    private String M210_ProposeIssueVc(String vcPlanId, String issuer, String offerId){
        P210RequestVo requestVo = new P210RequestVo(CaUtil.createMessageId(context));
        requestVo.setVcPlanId(vcPlanId);
        requestVo.setIssuer(issuer);
        requestVo.setOfferId(offerId);
        String request = requestVo.toJson();
        return request;
    }

    private String M210_RequestEcdh(String result){
        P210RequestVo requestVo = new P210RequestVo(CaUtil.createMessageId(context), MessageUtil.deserialize(result, P210ResponseVo.class).getTxId());

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
        } catch (WalletException | UtilityException | WalletCoreException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        requestVo.setReqEcdh(reqEcdh);

        return requestVo.toJson();
    }

    private String M210_RequestCreateToken(ServerTokenSeed serverTokenSeed){
        P210RequestVo requestVo = new P210RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setSeed(serverTokenSeed);
        String request = requestVo.toJson();
        return request;
    }

    private String M210_RequestIssueProfile(String result){
        P210RequestVo requestVo = new P210RequestVo(CaUtil.createMessageId(context), txId);
        this.serverToken = result;
        requestVo.setServerToken(serverToken);
        String request = requestVo.toJson();
        return request;
    }

    private String M210_RequestIssueVc(String txId, String serverToken, String refId, IssueProfile profile, DIDAuth signedDIDAuth){

        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        resultHolder[0] = "";

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    String result = walletApi.requestIssueVc(hWalletToken, Config.TAS_URL, Config.API_GATEWAY_URL, serverToken, refId, profile, signedDIDAuth, txId).get();
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

    private String M210_ConfirmIssueVc(String vcId){
        P210RequestVo requestVo = new P210RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setServerToken(serverToken);
        requestVo.setVcId(vcId);
        String request = requestVo.toJson();
        return request;
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        WalletTokenSeed walletTokenSeed = new WalletTokenSeed();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        } catch (WalletException | WalletCoreException | UtilityException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return walletTokenSeed.toJson();
    }

    private ServerTokenSeed createServerTokenSeed(String result) {
        ServerTokenSeed serverTokenSeed = new ServerTokenSeed();
        serverTokenSeed.setPurpose(ServerTokenPurpose.SERVER_TOKEN_PURPOSE.ISSUE_VC);
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
        String request = createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.ISSUE_VC);
        return request;
    }
    private String M000_GetAttestedAppInfo(String appId){
        JSONObject json = new JSONObject();
        try {
            json.put("appId", appId);
        } catch (JSONException e) {
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
        issueVc(signedDIDAuth, navController);
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
                        issueVc(signedDIDAuth, navController);
                    } catch (WalletException | UtilityException | WalletCoreException e) {
                        CaLog.e("bio ahentication fail  " + e.getMessage());
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
                    CaLog.e("bio onFail");
                }
            });
            walletApi.authenticateBioKey(fragment, context);
        } catch (WalletException | WalletCoreException | UtilityException e) {
            CaLog.e("bio authentication fail : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }

    private void issueVc(DIDAuth signedDIDAuth, NavController navController){
        P210ResponseVo vcPofile = MessageUtil.deserialize(profile, P210ResponseVo.class);
        try {
            issueVcProcess(vcPofile.getProfile(), signedDIDAuth).get();
        } catch (ExecutionException | WalletException | InterruptedException e) {
            CaLog.e("issueVC error : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        Bundle bundle = new Bundle();
        bundle.putString("type",Constants.TYPE_ISSUE);
        navController.navigate(R.id.action_profileFragment_to_resultFragment, bundle);
    }
}
